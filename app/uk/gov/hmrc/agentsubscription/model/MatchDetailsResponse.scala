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

import play.api.libs.json.{JsString, JsValue, Writes}

sealed trait MatchDetailsResponse

object MatchDetailsResponse {
  case object Match extends MatchDetailsResponse
  case object NoMatch extends MatchDetailsResponse
  case object RecordNotFound extends MatchDetailsResponse
  case object InvalidIdentifier extends MatchDetailsResponse
  case object NotAllowed extends MatchDetailsResponse
  case object UnknownError extends MatchDetailsResponse

  implicit val matchDetailsWrites: Writes[MatchDetailsResponse] = new Writes[MatchDetailsResponse] {
    override def writes(o: MatchDetailsResponse): JsValue = o match {
      case Match             => JsString("match_successful")
      case NoMatch           => JsString("no_match")
      case RecordNotFound    => JsString("record_not_found")
      case InvalidIdentifier => JsString("invalid_identifier")
      case NotAllowed        => JsString("not_allowed")
      case UnknownError      => JsString("unknown_error")
    }
  }
}
