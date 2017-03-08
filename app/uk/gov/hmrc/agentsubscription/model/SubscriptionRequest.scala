/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import uk.gov.hmrc.domain.{SimpleObjectReads, SimpleObjectWrites}

object Arn {
  implicit val arnReads = new SimpleObjectReads[Arn]("arn", Arn.apply)
  implicit val arnWrites = new SimpleObjectWrites[Arn](_.arn)
}

object Address {
  implicit val format = Json.format[Address]
}

object Agency {
  implicit val writes: Writes[Agency] = Json.writes[Agency]
  implicit val reads: Reads[Agency] = (
      (__ \ "name").read[String](nonEmptyStringWithMaxLength(40)) and
      (__ \ "address").read[Address] and
      (__ \ "telephone").read[String](telephoneNumber) and
      (__ \ "email").read[String](email)
  )(Agency.apply _)
}

object KnownFacts {
  implicit val format = Json.format[KnownFacts]
}

object SubscriptionRequest {
  implicit val format = Json.format[SubscriptionRequest]
}

case class Arn(arn: String)

case class Address(addressLine1: String,
                   addressLine2: String,
                   addressLine3: Option[String],
                   addressLine4: Option[String],
                   postcode: String,
                   countryCode: String
                  )

case class Agency(name: String,
                  address: Address,
                  telephone: String,
                  email: String
                 )

case class KnownFacts(postcode: String)

case class SubscriptionRequest(utr: String,
                               knownFacts: KnownFacts,
                               agency: Agency
                               )

case class SubscriptionResponse(arn: Arn)
object SubscriptionResponse {
  implicit val format = Json.format[SubscriptionResponse]
}
