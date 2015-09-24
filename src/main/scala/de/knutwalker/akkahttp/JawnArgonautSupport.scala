/*
 * Copyright 2015 Paul Horn
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

package de.knutwalker.akkahttp

import akka.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import akka.stream.scaladsl.{ FlattenStrategy, Flow, Keep, Sink }
import akka.stream.stage.{ Context, PushPullStage, SyncDirective, TerminationDirective }
import akka.stream.{ Materializer, stage }
import akka.util.ByteString
import argonaut.{ DecodeJson, _ }
import de.knutwalker.akkahttp.JawnArgonautSupport.{ SingleValueStreamingParser, StreamingParser }
import jawn.AsyncParser
import jawn.AsyncParser.ValueStream
import jawn.support.argonaut.Parser._

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.language.implicitConversions

import java.nio.ByteBuffer

object JawnArgonautSupport extends JawnArgonautSupport {
  final class StreamParser(parser: AsyncParser[Json]) extends PushPullStage[ByteString, Json] {
    private[this] var parsedJsons  : List[Json]           = Nil
    private[this] var unparsedJsons: Iterator[ByteBuffer] = Iterator()

    def onPush(elem: ByteString, ctx: stage.Context[Json]): SyncDirective = {
      if (parsedJsons.nonEmpty) {
        pushAlreadyParsedJson(ctx)
      } else if (unparsedJsons.isEmpty) {
        parseLoop(elem.asByteBuffers.iterator, ctx)
      } else {
        unparsedJsons ++= elem.asByteBuffers.iterator
        parseLoop(unparsedJsons, ctx)
      }
    }

    def onPull(ctx: Context[Json]): SyncDirective = {
      if (parsedJsons.nonEmpty) {
        pushAlreadyParsedJson(ctx)
      } else {
        parseLoop(unparsedJsons, ctx)
      }
    }

    override def onUpstreamFinish(ctx: Context[Json]): TerminationDirective = {
      ctx.absorbTermination()
    }

    private def pushAlreadyParsedJson(ctx: Context[Json]): SyncDirective = {
      val json = parsedJsons.head
      parsedJsons = parsedJsons.tail
      ctx.push(json)
    }

    private def pushAndBufferJson(jsons: Seq[Json], ctx: Context[Json]): SyncDirective = {
      parsedJsons = jsons.tail.toList
      ctx.push(jsons.head)
    }

    @tailrec
    private[this] def parseLoop(in: Iterator[ByteBuffer], ctx: stage.Context[Json]): SyncDirective = {
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

  def StreamingParser: Flow[ByteString, Json, Unit] =
    Flow[ByteString].transform(() ⇒ new StreamParser(async(ValueStream)))

  def SingleValueStreamingParser: Sink[ByteString, Future[Json]] =
    StreamingParser.toMat(Sink.head)(Keep.right)
}
trait JawnArgonautSupport {

  def parse[A](implicit A: DecodeJson[A]): Flow[ByteString, A, Unit] =
    StreamingParser.map(parseJson(A))

  def parseEntity[A](implicit A: DecodeJson[A]): Flow[HttpEntity, A, Unit] =
    Flow[HttpEntity].map(_.dataBytes.via(parse[A])).flatten(FlattenStrategy.concat[A])

  implicit def argonautUnmarshallerConverter[A](A: DecodeJson[A])(implicit mat: Materializer): FromEntityUnmarshaller[A] =
    argonautUnmarshaller(A, mat)

  implicit def argonautUnmarshaller[A](implicit A: DecodeJson[A], mat: Materializer): FromEntityUnmarshaller[A] =
    argonautJsonUnmarshaller.map(parseJson(A))

  implicit def argonautJsonUnmarshaller(implicit mat: Materializer): FromEntityUnmarshaller[Json] =
    Unmarshaller {implicit ec ⇒ (e: HttpEntity) ⇒ e.dataBytes.runWith(SingleValueStreamingParser)}
    .forContentTypes(`application/json`)

  implicit def argonautMarshallerConverter[A](A: EncodeJson[A])(implicit P: PrettyParams = Argonaut.nospace): ToEntityMarshaller[A] =
    argonautMarshaller[A](A, P)

  implicit def argonautMarshaller[A](implicit A: EncodeJson[A], P: PrettyParams = Argonaut.nospace): ToEntityMarshaller[A] =
    argonautJsonMarshaller[A].compose(A.encode)

  implicit def argonautJsonMarshaller[A](implicit A: EncodeJson[A], P: PrettyParams = Argonaut.nospace): ToEntityMarshaller[Json] =
    Marshaller.StringMarshaller.wrap(ContentTypes.`application/json`)(P.pretty)

  private[this] def parseJson[A](decoder: DecodeJson[A])(json: Json): A = {
    val cursor = json.hcursor
    decoder.decode(cursor).fold((_, hist) ⇒ throw new IllegalArgumentException(errorMessage(hist, cursor)), identity)
  }

  private[this] def errorMessage(hist: CursorHistory, cursor: HCursor) = {
    val field = fieldFromHistory(hist)
    val down = cursor.downField(field)
    if (down.succeeded) {
      s"Could not decode [${down.focus.get}] at [$field]."
    } else {
      s"The field [$field] is missing."
    }
  }

  private[this] def fieldFromHistory(c: CursorHistory): String =
    fieldFromHistory0(c.toList, 0, Nil)

  @tailrec
  private[this] def fieldFromHistory0(hist: List[CursorOp], arrayIndex: Int, out: List[String]): String = hist match {
    case some :: rest ⇒ some match {
      case El(CursorOpRight, _)        ⇒ fieldFromHistory0(rest, arrayIndex + 1, out)
      case El(CursorOpLeft, _)         ⇒ fieldFromHistory0(rest, arrayIndex - 1, out)
      case El(CursorOpDownArray, _)    ⇒ fieldFromHistory0(rest, 0, s"[$arrayIndex]" :: out)
      case El(CursorOpDownField(f), _) ⇒ fieldFromHistory0(rest, arrayIndex, f :: out)
      case _                           ⇒ fieldFromHistory0(rest, arrayIndex, out)
    }
    case Nil          ⇒ out.mkString(".")
  }
}
