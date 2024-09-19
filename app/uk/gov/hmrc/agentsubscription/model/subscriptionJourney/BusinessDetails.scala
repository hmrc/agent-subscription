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
import uk.gov.hmrc.agentsubscription.model.DateOfBirth
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypterDecrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

/** Information about the agent's business. They must always provide a business type, UTR and postcode. But other data
  * points are only required for some business types and if certain conditions are NOT met e.g. if they provide a NINO,
  * they must provide date of birth if they are registered for vat, they must provide vat details The record is created
  * once we have the minimum business details
  */

case class BusinessDetails(
  businessType: BusinessType,
  utr: String, // CT or SA
  postcode: String,
  registration: Option[Registration] = None,
  nino: Option[String] = None,
  companyRegistrationNumber: Option[CompanyRegistrationNumber] = None,
  dateOfBirth: Option[DateOfBirth] = None, // if NINO required
  registeredForVat: Option[Boolean] = None,
  vatDetails: Option[VatDetails] = None
) // if registered for VAT

object BusinessDetails {
  implicit val format: OFormat[BusinessDetails] = Json.format
  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[BusinessDetails] =
    (
      (__ \ "businessType").format[BusinessType] and
        (__ \ "utr").format[String](stringEncrypterDecrypter) and
        (__ \ "postcode").format[String](stringEncrypterDecrypter) and
        (__ \ "registration").formatNullable[Registration](Registration.databaseFormat) and
        (__ \ "nino").formatNullable[String](stringEncrypterDecrypter) and
        (__ \ "companyRegistrationNumber").formatNullable[CompanyRegistrationNumber] and
        (__ \ "dateOfBirth").formatNullable[DateOfBirth] and
        (__ \ "registeredForVat").formatNullable[Boolean] and
        (__ \ "vatDetails").formatNullable[VatDetails]
    )(BusinessDetails.apply, unlift(BusinessDetails.unapply))
}
