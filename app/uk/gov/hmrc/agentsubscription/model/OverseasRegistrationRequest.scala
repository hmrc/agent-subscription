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

package uk.gov.hmrc.agentsubscription.model

import java.util.UUID

import play.api.libs.json.{ Json, OFormat }

case class ContactDetails(phoneNumber: String, emailAddress: String)

object ContactDetails {
  implicit val contactDetailsFormat: OFormat[ContactDetails] = Json.format
}

case class Organisation(organisationName: String)

object Organisation {
  implicit val organisationFormat: OFormat[Organisation] = Json.format
}

import OverseasAddress._

case class OverseasRegistrationRequest(
  regime: String,
  acknowledgementReference: String,
  isAnAgent: Boolean,
  isAGroup: Boolean,
  organisation: Organisation,
  address: OverseasAddress,
  contactDetails: ContactDetails)

object OverseasRegistrationRequest {

  implicit val overseasRegistrationRequestFormat: OFormat[OverseasRegistrationRequest] = Json.format

  def apply(fromApplication: CurrentApplication): OverseasRegistrationRequest = {
    OverseasRegistrationRequest(
      regime = "AGSV",
      acknowledgementReference = UUID.randomUUID.toString.replaceAll("-", ""),
      isAnAgent = false,
      isAGroup = false,
      Organisation(organisationName = fromApplication.tradingDetails.tradingName),
      address = maybeUkAddress(fromApplication.tradingDetails.tradingAddress),
      contactDetails = ContactDetails(
        phoneNumber = fromApplication.businessContactDetails.businessTelephone,
        emailAddress = fromApplication.businessContactDetails.businessEmail))
  }

}

