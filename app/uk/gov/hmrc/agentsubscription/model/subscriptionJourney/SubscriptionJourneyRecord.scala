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

package uk.gov.hmrc.agentsubscription.model.subscriptionJourney

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.AmlsData
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

import java.time.LocalDateTime

/** A Mongo record which represents the user's current journey in setting up a new MTD Agent Services account, with
  * their existing relationships.
  */

final case class SubscriptionJourneyRecord(
  authProviderId: AuthProviderId,
  continueId: Option[String], // once allocated, should not be changed?
  businessDetails: BusinessDetails,
  amlsData: Option[AmlsData],
  userMappings: List[UserMapping],
  mappingComplete: Boolean,
  cleanCredsAuthProviderId: Option[AuthProviderId],
  lastModifiedDate: Option[LocalDateTime],
  contactEmailData: Option[ContactEmailData],
  contactTradingNameData: Option[ContactTradingNameData],
  contactTradingAddressData: Option[ContactTradingAddressData],
  contactTelephoneData: Option[ContactTelephoneData],
  verifiedEmails: VerifiedEmails = VerifiedEmails(emails = Set.empty)
)

object SubscriptionJourneyRecord {

  import MongoLocalDateTimeFormat._

  def databaseWrites(crypto: Encrypter with Decrypter): Writes[SubscriptionJourneyRecord] =
    ((JsPath \ "authProviderId").write[AuthProviderId] and
      (JsPath \ "continueId").writeNullable[String] and
      (JsPath \ "businessDetails").write[BusinessDetails](BusinessDetails.databaseFormat(crypto)) and
      (JsPath \ "amlsData").writeNullable[AmlsData] and
      (JsPath \ "userMappings").write[List[UserMapping]] and
      (JsPath \ "mappingComplete").write[Boolean] and
      (JsPath \ "cleanCredsAuthProviderId").writeNullable[AuthProviderId] and
      (JsPath \ "lastModifiedDate").writeNullable[LocalDateTime] and
      (JsPath \ "contactEmailData").writeNullable[ContactEmailData](ContactEmailData.databaseFormat(crypto)) and
      (JsPath \ "contactTradingNameData").writeNullable[ContactTradingNameData](
        ContactTradingNameData.databaseFormat(crypto)
      ) and
      (JsPath \ "contactTradingAddressData").writeNullable[ContactTradingAddressData](
        ContactTradingAddressData.databaseFormat(crypto)
      ) and
      (JsPath \ "contactTelephoneData").writeNullable[ContactTelephoneData](
        ContactTelephoneData.databaseFormat(crypto)
      ) and
      (JsPath \ "verifiedEmails").write[VerifiedEmails](
        VerifiedEmails.databaseFormat(crypto)
      ))(
      unlift(SubscriptionJourneyRecord.unapply)
    )

  def databaseReads(crypto: Encrypter with Decrypter): Reads[SubscriptionJourneyRecord] =
    ((JsPath \ "authProviderId").read[AuthProviderId] and
      (JsPath \ "continueId").readNullable[String] and
      (JsPath \ "businessDetails").read[BusinessDetails](BusinessDetails.databaseFormat(crypto)) and
      (JsPath \ "amlsData").readNullable[AmlsData] and
      (JsPath \ "userMappings").read[List[UserMapping]] and
      (JsPath \ "mappingComplete").read[Boolean] and
      (JsPath \ "cleanCredsAuthProviderId").readNullable[AuthProviderId] and
      (JsPath \ "lastModifiedDate").readNullable[LocalDateTime] and
      (JsPath \ "contactEmailData").readNullable[ContactEmailData](ContactEmailData.databaseFormat(crypto)) and
      (JsPath \ "contactTradingNameData").readNullable[ContactTradingNameData](
        ContactTradingNameData.databaseFormat(crypto)
      ) and
      (JsPath \ "contactTradingAddressData").readNullable[ContactTradingAddressData](
        ContactTradingAddressData.databaseFormat(crypto)
      ) and
      (JsPath \ "contactTelephoneData").readNullable[ContactTelephoneData](
        ContactTelephoneData.databaseFormat(crypto)
      ) and
      (JsPath \ "verifiedEmails")
        .read[VerifiedEmails](VerifiedEmails.databaseFormat(crypto)))(SubscriptionJourneyRecord.apply _)

  def databaseFormat(crypto: Encrypter with Decrypter): Format[SubscriptionJourneyRecord] =
    Format(databaseReads(crypto), sjr => databaseWrites(crypto).writes(sjr))

  implicit val writes: Writes[SubscriptionJourneyRecord] = Json.writes[SubscriptionJourneyRecord]

}
