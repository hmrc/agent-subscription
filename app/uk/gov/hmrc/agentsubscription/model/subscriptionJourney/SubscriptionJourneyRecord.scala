/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.LocalDateTime

import play.api.libs.functional.syntax._
import play.api.libs.json.{ JsPath, Json, OFormat }
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.connectors.BusinessAddress
import uk.gov.hmrc.agentsubscription.model.{ AuthProviderId, ContactEmailData, DateOfBirth }
import uk.gov.hmrc.agentsubscriptionfrontend.models.subscriptionJourney.AmlsData
import uk.gov.hmrc.domain.Nino

/**
 * A Mongo record which represents the user's current journey in setting up a new
 * MTD Agent Services account, with their existing relationships.
 *
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
  contactDetailsTradingName: Option[String],
  contactDetailsTradingAddress: Option[BusinessAddress])

object SubscriptionJourneyRecord {

  import MongoLocalDateTimeFormat._

  implicit val subscriptionJourneyFormat: OFormat[SubscriptionJourneyRecord] =
    ((JsPath \ "authProviderId").format[AuthProviderId] and
      (JsPath \ "continueId").formatNullable[String] and
      (JsPath \ "businessDetails").format[BusinessDetails] and
      (JsPath \ "amlsData").formatNullable[AmlsData] and
      (JsPath \ "userMappings").format[List[UserMapping]] and
      (JsPath \ "mappingComplete").format[Boolean] and
      (JsPath \ "cleanCredsAuthProviderId").formatNullable[AuthProviderId] and
      (JsPath \ "lastModifiedDate").formatNullable[LocalDateTime] and
      (JsPath \ "contactEmailData").formatNullable[ContactEmailData] and
      (JsPath \ "contactDetailsTradingName").formatNullable[String] and
      (JsPath \ "contactDetailsTradingAddress")
      .formatNullable[BusinessAddress])(SubscriptionJourneyRecord.apply, unlift(SubscriptionJourneyRecord.unapply))
}

/**
 * Information about the agent's business.  They must always provide a business type, UTR and postcode.
 * But other data points are only required for some business types and if certain conditions are NOT met
 * e.g.
 *   if they provide a NINO, they must provide date of birth
 *   if they are registered for vat, they must provide vat details
 * The record is created once we have the minimum business details
 */
case class BusinessDetails(
  businessType: BusinessType,
  utr: Utr, // CT or SA
  postcode: Postcode,
  registration: Option[Registration] = None,
  nino: Option[Nino] = None,
  companyRegistrationNumber: Option[CompanyRegistrationNumber] = None,
  dateOfBirth: Option[DateOfBirth] = None, // if NINO required
  registeredForVat: Option[Boolean] = None,
  vatDetails: Option[VatDetails] = None) // if registered for VAT

object BusinessDetails {
  implicit val format: OFormat[BusinessDetails] = Json.format
}

