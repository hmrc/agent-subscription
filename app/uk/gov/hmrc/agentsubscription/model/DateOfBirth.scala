/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json._

import scala.util.Failure
import scala.util.Success
import scala.util.Try

case class DateOfBirth(value: LocalDate)

object DateOfBirth {

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  implicit val format: Format[DateOfBirth] =
    new Format[DateOfBirth] {
      override def writes(o: DateOfBirth): JsValue = JsString(o.value.format(formatter))

      override def reads(json: JsValue): JsResult[DateOfBirth] =
        json match {
          case JsString(s) =>
            Try(LocalDate.parse(s, formatter)) match {
              case Success(date) => JsSuccess(DateOfBirth(date))
              case Failure(error) => JsError(s"Could not parse date as yyyy-MM-dd: ${error.getMessage}")
            }
          case other => JsError(s"Expected string but got $other")
        }
    }

}
