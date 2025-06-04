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
import uk.gov.hmrc.agentsubscription.model.BusinessAddress
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypterDecrypter
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter

case class Registration(
  taxpayerName: Option[String],
  isSubscribedToAgentServices: Boolean,
  isSubscribedToETMP: Boolean,
  address: BusinessAddress,
  emailAddress: Option[String],
  primaryPhoneNumber: Option[String],
  safeId: Option[String]
)

object Registration {

  def databaseFormat(implicit
    crypto: Encrypter
      with Decrypter
  ): Format[Registration] =
    (
      (__ \ "taxpayerName").formatNullable[String](stringEncrypterDecrypter) and
        (__ \ "isSubscribedToAgentServices").format[Boolean] and
        (__ \ "isSubscribedToETMP").format[Boolean] and
        (__ \ "address").format[BusinessAddress](BusinessAddress.databaseFormat(crypto)) and
        (__ \ "emailAddress").formatNullable[String](stringEncrypterDecrypter) and
        (__ \ "primaryPhoneNumber").formatNullable[String](stringEncrypterDecrypter) and
        (__ \ "safeId").formatNullable[String]
    )(Registration.apply, unlift(Registration.unapply))

  implicit val format: OFormat[Registration] = Json.format[Registration]

}

case class UpdateBusinessAddressForm(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  postCode: String
)

object UpdateBusinessAddressForm {
  def apply(businessAddress: BusinessAddress): UpdateBusinessAddressForm = UpdateBusinessAddressForm(
    businessAddress.addressLine1,
    businessAddress.addressLine2,
    businessAddress.addressLine3,
    businessAddress.addressLine4,
    businessAddress.postalCode.getOrElse(throw new Exception("Postcode is mandatory"))
  )
}
