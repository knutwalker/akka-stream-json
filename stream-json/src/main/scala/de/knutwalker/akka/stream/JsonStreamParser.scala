/*
 * Copyright 2015 – 2018 Paul Horn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.knutwalker.akka.stream

import akka.NotUsed
import akka.stream.ActorAttributes.SupervisionStrategy
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, FlowShape, Graph, Inlet, Outlet, Supervision }
import akka.util.ByteString
import jawn.AsyncParser.ValueStream
import jawn.{ AsyncParser, RawFacade, ParseException, Parser }

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal
import java.nio.ByteBuffer

object JsonStreamParser {

  def apply[J: RawFacade]: Graph[FlowShape[ByteString, J], NotUsed] =
    apply[J](ValueStream)

  def apply[J: RawFacade](mode: AsyncParser.Mode): Graph[FlowShape[ByteString, J], NotUsed] =
    new JsonStreamParser(mode)

  def flow[J: RawFacade]: Flow[ByteString, J, NotUsed] =
    Flow.fromGraph(apply[J])

  def flow[J: RawFacade](mode: AsyncParser.Mode): Flow[ByteString, J, NotUsed] =
    Flow.fromGraph(apply[J](mode))

  def head[J: RawFacade]: Sink[ByteString, Future[J]] =
    flow.toMat(Sink.head)(Keep.right)

  def head[J: RawFacade](mode: AsyncParser.Mode): Sink[ByteString, Future[J]] =
    flow(mode).toMat(Sink.head)(Keep.right)

  def headOption[J: RawFacade]: Sink[ByteString, Future[Option[J]]] =
    flow.toMat(Sink.headOption)(Keep.right)

  def headOption[J: RawFacade](mode: AsyncParser.Mode): Sink[ByteString, Future[Option[J]]] =
    flow(mode).toMat(Sink.headOption)(Keep.right)

  def parse[J: RawFacade](bytes: ByteString): Try[J] =
    Parser.parseFromByteBuffer(bytes.asByteBuffer)

  private final class ParserLogic[J: RawFacade](parser: AsyncParser[J], shape: FlowShape[ByteString, J], attr: Attributes) extends GraphStageLogic(shape) with InHandler with OutHandler {
    private[this] val in      = shape.in
    private[this] val out     = shape.out
    private[this] val scratch = new ArrayBuffer[J](64)

    private[this] lazy val decider = attr.get[SupervisionStrategy].map(_.decider).getOrElse(Supervision.stoppingDecider)

    setHandlers(in, out, this)

    def onPull(): Unit = tryPull(in)

    def onPush(): Unit = {
      scratch.clear()
      val input = grab(in).asByteBuffers
      emitOrPullLoop(input.iterator, scratch)
    }

    override def onUpstreamFinish(): Unit = {
      parser.finish() match {
        case Left(ParseException("exhausted input", _, _, _)) => completeStage()
        case Left(e)                                          => if (onException(e)) completeStage()
        case Right(jsons)                                     => emitMultiple(out, jsons.iterator, completeStage _)
      }
    }

    override def onDownstreamFinish(): Unit = {
      parser.finish()
      completeStage()
    }

    private[this] def emitOrPullLoop(bs: Iterator[ByteBuffer], results: ArrayBuffer[J]): Unit = {
      while (bs.hasNext) {
        val next = bs.next()
        val shouldContinue = try {
          val absorb = parser.absorb(next)
          absorb match {
            case Left(e)      =>
              onException(e)
            case Right(jsons) =>
              if (jsons.nonEmpty) {
                results ++= jsons
              }
              true
          }
        } catch {
          case NonFatal(ex) => onException(ex)
        }
        if (!shouldContinue) {
          while (bs.hasNext) {
            bs.next()
          }
        }
      }
      if (results.nonEmpty) {
        emitMultiple(out, results.iterator)
      } else if (!hasBeenPulled(in)) {
        tryPull(in)
      }
    }

    private def onException(ex: Throwable): Boolean = decider(ex) match {
      case Supervision.Stop                         => failStage(ex); false
      case Supervision.Restart | Supervision.Resume => true
    }
  }
}

final class JsonStreamParser[J: RawFacade] private(mode: AsyncParser.Mode) extends GraphStage[FlowShape[ByteString, J]] {
  private[this] val in    = Inlet[ByteString]("Json.in")
  private[this] val out   = Outlet[J]("Json.out")
  override      val shape = FlowShape(in, out)

  override def initialAttributes: Attributes = Attributes.name(s"jsonStream($mode)")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new JsonStreamParser.ParserLogic[J](AsyncParser[J](mode), shape, inheritedAttributes)
}
