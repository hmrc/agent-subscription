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

import play.api.libs.json.{Format, JsResult, JsValue, Json, Reads, Writes}
import uk.gov.hmrc.agentsubscription.repository.EncryptionUtils.decryptOptString
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

case class ContactTelephoneData(
  useBusinessTelephone: Boolean,
  telephoneNumber: Option[String],
  encrypted: Option[Boolean] = None
)

object ContactTelephoneData {
  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[ContactTelephoneData] = {

    def reads(json: JsValue): JsResult[ContactTelephoneData] =
      for {
        isEncrypted          <- (json \ "encrypted").validateOpt[Boolean]
        useBusinessTelephone <- (json \ "useBusinessTelephone").validate[Boolean]
        telephoneNumber = decryptOptString("telephoneNumber", isEncrypted, json)
      } yield ContactTelephoneData(
        useBusinessTelephone,
        telephoneNumber,
        isEncrypted
      )

    def writes(contactTelephoneData: ContactTelephoneData): JsValue =
      Json.obj(
        "useBusinessTelephone" -> contactTelephoneData.useBusinessTelephone,
        "telephoneNumber"      -> contactTelephoneData.telephoneNumber.map(stringEncrypter.writes),
        "encrypted"            -> Some(true)
      )

    Format(reads(_), contactTelephoneData => writes(contactTelephoneData))
  }

  implicit val writes: Writes[ContactTelephoneData] = Json.writes[ContactTelephoneData]
  implicit val reads: Reads[ContactTelephoneData] = Json.reads[ContactTelephoneData]
}
