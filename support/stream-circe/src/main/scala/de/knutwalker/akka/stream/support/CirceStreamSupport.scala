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
package support

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import cats.data.Xor

import io.circe.jawn.CirceSupportParser._
import io.circe.{ CursorOp, Decoder, Encoder, HCursor, HistoryOp, Json, Printer }
import jawn.AsyncParser

import scala.annotation.tailrec
import scala.language.implicitConversions

object CirceStreamSupport extends CirceStreamSupport

trait CirceStreamSupport {

  def decode[A: Decoder]: Flow[ByteString, A, NotUsed] =
    JsonStreamParser.flow[Json].map(decodeJson[A])

  def decode[A: Decoder](mode: AsyncParser.Mode): Flow[ByteString, A, NotUsed] =
    JsonStreamParser.flow[Json](mode).map(decodeJson[A])

  def encode[A](implicit A: Encoder[A], P: Printer = Printer.noSpaces): Flow[A, String, NotUsed] =
    Flow[A].map(a ⇒ P.pretty(A(a)))

  private[knutwalker] def decodeJson[A](json: Json)(implicit decoder: Decoder[A]): A = {
    val cursor = json.hcursor
    decoder(cursor) match {
      case Xor.Right(e) ⇒ e
      case Xor.Left(f)  ⇒ throw new IllegalArgumentException(errorMessage(f.history, cursor, f.message))
    }
  }


  private[this] def errorMessage(hist: List[HistoryOp], cursor: HCursor, typeHint: String) = {
    val field = fieldFromHistory(hist)
    val down = cursor.downField(field)
    if (down.succeeded) {
      s"Could not decode [${down.focus.get}] at [$field] as [$typeHint]."
    } else {
      s"The field [$field] is missing."
    }
  }

  @tailrec
  private[this] def fieldFromHistory(hist: List[HistoryOp], arrayIndex: Int = 0, out: List[String] = Nil): String = hist match {
    case some :: rest ⇒ some.op match {
      case Some(CursorOp.MoveRight)    ⇒ fieldFromHistory(rest, arrayIndex + 1, out)
      case Some(CursorOp.MoveLeft)     ⇒ fieldFromHistory(rest, arrayIndex - 1, out)
      case Some(CursorOp.DownArray)    ⇒ fieldFromHistory(rest, 0, s"[$arrayIndex]" :: out)
      case Some(CursorOp.DownField(f)) ⇒ fieldFromHistory(rest, arrayIndex, f :: out)
      case _                           ⇒ fieldFromHistory(rest, arrayIndex, out)
    }
    case Nil          ⇒ out.mkString(".")
  }
}
