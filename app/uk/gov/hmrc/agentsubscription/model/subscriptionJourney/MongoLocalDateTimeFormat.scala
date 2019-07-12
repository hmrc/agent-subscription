/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.model.subscriptionJourney

import java.time.{ Instant, LocalDateTime, ZoneOffset }

import play.api.libs.json.{ Format, JsResult, JsSuccess, JsValue, Json }

object MongoLocalDateTimeFormat {

  // LocalDateTime must be written to DB as ISODate to allow the expiry TTL on createdOn date to work

  implicit val localDateTimeFormats: Format[LocalDateTime] = new Format[LocalDateTime] {

    override def writes(o: LocalDateTime): JsValue = Json.obj("$date" -> o.atZone(ZoneOffset.UTC).toInstant.toEpochMilli)

    override def reads(json: JsValue): JsResult[LocalDateTime] = {
      JsSuccess(LocalDateTime.ofInstant(Instant.ofEpochMilli((json \ "$date").as[Long]), ZoneOffset.UTC))
    }
  }

}
