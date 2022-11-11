/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{Format, Reads, Writes, __}

import java.time.{Instant, LocalDateTime, ZoneOffset}

object MongoLocalDateTimeFormat {

  // LocalDateTime must be written to DB as ISODate to allow the expiry TTL on createdOn date to work

  final val localDateTimeReads: Reads[LocalDateTime] =
    Reads
      .at[String](__ \ "$date" \ "$numberLong")
      .map { dateTime =>
        Instant.ofEpochMilli(dateTime.toLong).atZone(ZoneOffset.UTC).toLocalDateTime
      }

  // for data that exists prior to the hmrc-mongo migration - TODO remove this after legacy data expires (30 days)
  final val legacyDateTimeReads: Reads[LocalDateTime] =
    Reads
      .at[String](__)
      .map(dateTime => LocalDateTime.parse(dateTime))

  final val localDateTimeWrites: Writes[LocalDateTime] =
    Writes
      .at[String](__ \ "$date" \ "$numberLong")
      .contramap(_.toInstant(ZoneOffset.UTC).toEpochMilli.toString)

  final implicit val localDateTimeFormat: Format[LocalDateTime] =
    Format(localDateTimeReads.orElse(legacyDateTimeReads), localDateTimeWrites)

}
