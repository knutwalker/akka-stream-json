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

import akka.stream.scaladsl.{ Flow, Keep, Sink }
import akka.stream.stage.{ Context, PushPullStage, Stage, SyncDirective, TerminationDirective }
import akka.util.ByteString

import jawn.AsyncParser.ValueStream
import jawn.{ AsyncParser, Facade, Parser }

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Try
import java.nio.ByteBuffer


object JsonStreamParser {

  def apply[J: Facade]: Stage[ByteString, J] =
    new JsonStreamParser(AsyncParser[J](ValueStream))

  def flow[J: Facade]: Flow[ByteString, J, Unit] =
    Flow[ByteString].transform(() ⇒ apply[J])

  def head[J: Facade]: Sink[ByteString, Future[J]] =
    flow.toMat(Sink.head)(Keep.right)

  def headOption[J: Facade]: Sink[ByteString, Future[Option[J]]] =
    flow.toMat(Sink.headOption)(Keep.right)

  def parse[J: Facade](bytes: ByteString): Try[J] =
    Parser.parseFromByteBuffer(bytes.asByteBuffer)
}

final class JsonStreamParser[J](parser: AsyncParser[J])(implicit facade: Facade[J]) extends PushPullStage[ByteString, J] {
  private[this] var parsedJsons  : List[J]              = Nil
  private[this] var unparsedJsons: Iterator[ByteBuffer] = Iterator()

  def onPush(elem: ByteString, ctx: Context[J]): SyncDirective = {
    if (parsedJsons.nonEmpty) {
      unparsedJsons ++= elem.asByteBuffers.iterator
      pushAlreadyParsedJson(ctx)
    } else if (unparsedJsons.isEmpty) {
      parseLoop(elem.asByteBuffers.iterator, ctx)
    } else {
      unparsedJsons ++= elem.asByteBuffers.iterator
      parseLoop(unparsedJsons, ctx)
    }
  }

  def onPull(ctx: Context[J]): SyncDirective = {
    if (parsedJsons.nonEmpty) {
      pushAlreadyParsedJson(ctx)
    } else {
      parseLoop(unparsedJsons, ctx)
    }
  }

  override def onUpstreamFinish(ctx: Context[J]): TerminationDirective = {
    ctx.absorbTermination()
  }

  private def pushAlreadyParsedJson(ctx: Context[J]): SyncDirective = {
    val json = parsedJsons.head
    parsedJsons = parsedJsons.tail
    ctx.push(json)
  }

  private def pushAndBufferJson(jsons: Seq[J], ctx: Context[J]): SyncDirective = {
    parsedJsons = jsons.tail.toList
    ctx.push(jsons.head)
  }

  @tailrec
  private[this] def parseLoop(in: Iterator[ByteBuffer], ctx: Context[J]): SyncDirective = {
    if (in.hasNext) {
      val next = in.next()
      val absorb = parser.absorb(next)
      absorb match {
        case Left(e)      ⇒ ctx.fail(e)
        case Right(jsons) ⇒ jsons.size match {
          case 0 ⇒ parseLoop(in, ctx)
          case 1 ⇒ ctx.push(jsons.head)
          case _ ⇒ pushAndBufferJson({unparsedJsons = in; jsons}, ctx)
        }
      }
    } else {
      if (!ctx.isFinishing) {
        ctx.pull()
      } else {
        val finish = parser.finish()
        finish match {
          case Left(e)      ⇒ ctx.fail(e)
          case Right(jsons) ⇒ jsons.size match {
            case 1 ⇒ ctx.pushAndFinish(jsons.head)
            case 0 ⇒ ctx.finish()
            case _ ⇒ pushAndBufferJson(jsons, ctx)
          }
        }
      }
    }
  }
}
