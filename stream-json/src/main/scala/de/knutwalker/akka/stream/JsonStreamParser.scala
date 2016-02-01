/*
 * Copyright 2015 – 2016 Paul Horn
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
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import akka.stream.{ Attributes, FlowShape, Graph, Inlet, Outlet }
import akka.util.ByteString

import jawn.AsyncParser.ValueStream
import jawn.{ AsyncParser, Facade, ParseException, Parser }

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Try
import java.nio.ByteBuffer


object JsonStreamParser {

  def apply[J: Facade]: Graph[FlowShape[ByteString, J], NotUsed] =
    apply[J](ValueStream)

  def apply[J: Facade](mode: AsyncParser.Mode): Graph[FlowShape[ByteString, J], NotUsed] =
    new JsonStreamParser(mode)

  def flow[J: Facade]: Flow[ByteString, J, NotUsed] =
    Flow.fromGraph(apply[J])

  def head[J: Facade]: Sink[ByteString, Future[J]] =
    flow.toMat(Sink.head)(Keep.right)

  def headOption[J: Facade]: Sink[ByteString, Future[Option[J]]] =
    flow.toMat(Sink.headOption)(Keep.right)

  def parse[J: Facade](bytes: ByteString): Try[J] =
    Parser.parseFromByteBuffer(bytes.asByteBuffer)


  // TODO: use attributes to buffer elements
  private final class ParserLogic[J: Facade](parser: AsyncParser[J], shape: FlowShape[ByteString, J]) extends GraphStageLogic(shape) {
    private[this] val in      = shape.in
    private[this] val out     = shape.out
    private[this] val scratch = new ArrayBuffer[J](64)

    setHandler(out, new OutHandler {
      override def onPull() = pull(in)
      override def onDownstreamFinish() = downstreamFinish()
    })
    setHandler(in, new InHandler {
      override def onPush() = upstreamPush()
      override def onUpstreamFinish() = finishParser()
    })

    private def upstreamPush(): Unit = {
      scratch.clear()
      val input = grab(in).asByteBuffers
      emitOrPullLoop(input.iterator, scratch)
    }

    private def downstreamFinish(): Unit = {
      parser.finish()
      cancel(in)
    }

    private def finishParser(): Unit = {
      parser.finish() match {
        case Left(ParseException("exhausted input", _, _, _)) ⇒ complete(out)
        case Left(e)                                          ⇒ failStage(e)
        case Right(jsons)                                     ⇒ emitMultiple(out, jsons.iterator, () ⇒ complete(out))
      }
    }

    @tailrec
    private[this] def emitOrPullLoop(bs: Iterator[ByteBuffer], results: ArrayBuffer[J]): Unit = {
      if (bs.hasNext) {
        val next = bs.next()
        val absorb = parser.absorb(next)
        absorb match {
          case Left(e)      ⇒ failStage(e)
          case Right(jsons) ⇒
            if (jsons.nonEmpty) {
              results ++= jsons
            }
            emitOrPullLoop(bs, results)
        }
      } else {
        if (results.nonEmpty) {
          emitMultiple(out, results.iterator)
        } else {
          pull(in)
        }
      }
    }
  }
}

final class JsonStreamParser[J: Facade] private(mode: AsyncParser.Mode) extends GraphStage[FlowShape[ByteString, J]] {
  private[this] val in    = Inlet[ByteString]("Json.in")
  private[this] val out   = Outlet[J]("Json.out")
  override      val shape = FlowShape(in, out)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new JsonStreamParser.ParserLogic[J](AsyncParser[J](mode), shape)
}
