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

import play.api.libs.json.{Format, JsResult, JsValue, Json, Writes}
import uk.gov.hmrc.agentsubscription.model.BusinessAddress
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.agentsubscription.repository.EncryptionUtils._
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter

case class Registration(
  taxpayerName: Option[String],
  isSubscribedToAgentServices: Boolean,
  isSubscribedToETMP: Boolean,
  address: BusinessAddress,
  emailAddress: Option[String],
  primaryPhoneNumber: Option[String],
  safeId: Option[String],
  encrypted: Option[Boolean] = None
)

object Registration {

  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[Registration] = {

    def reads(json: JsValue): JsResult[Registration] =
      for {
        isEncrypted <- (json \ "encrypted").validateOpt[Boolean]
        taxpayerName = decryptOptString("taxpayerName", isEncrypted, json)
        isSubscribedToAgentServices = (json \ "isSubscribedToAgentServices").as[Boolean]
        isSubscribedToETMP = (json \ "isSubscribedToETMP").as[Boolean]
        address = (json \ "address").as[BusinessAddress](BusinessAddress.databaseFormat(crypto))
        emailAddress = decryptOptString("emailAddress", isEncrypted, json)
        primaryPhoneNumber = decryptOptString("primaryPhoneNumber", isEncrypted, json)
        safeId = (json \ "safeId").asOpt[String]
      } yield Registration(
        taxpayerName,
        isSubscribedToAgentServices,
        isSubscribedToETMP,
        address,
        emailAddress,
        primaryPhoneNumber,
        safeId,
        isEncrypted
      )

    def writes(registration: Registration): JsValue =
      Json.obj(
        "taxpayerName"                -> registration.taxpayerName.map(stringEncrypter.writes),
        "isSubscribedToAgentServices" -> registration.isSubscribedToAgentServices,
        "isSubscribedToETMP"          -> registration.isSubscribedToETMP,
        "address"                     -> BusinessAddress.databaseFormat.writes(registration.address),
        "emailAddress"                -> registration.emailAddress.map(stringEncrypter.writes),
        "primaryPhoneNumber"          -> registration.primaryPhoneNumber.map(stringEncrypter.writes),
        "safeId"                      -> registration.safeId,
        "encrypted"                   -> Some(true)
      )
    Format(reads(_), registration => writes(registration))
  }

  implicit val writes: Writes[Registration] = Json.writes[Registration]
}

case class UpdateBusinessAddressForm(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  postCode: String
)

object UpdateBusinessAddressForm {
  def apply(businessAddress: BusinessAddress): UpdateBusinessAddressForm =
    UpdateBusinessAddressForm(
      businessAddress.addressLine1,
      businessAddress.addressLine2,
      businessAddress.addressLine3,
      businessAddress.addressLine4,
      businessAddress.postalCode.getOrElse(throw new Exception("Postcode is mandatory"))
    )
}
