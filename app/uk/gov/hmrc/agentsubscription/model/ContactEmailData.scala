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

package uk.gov.hmrc.agentsubscription.model

import play.api.libs.json.{Format, JsResult, JsValue, Json}
import uk.gov.hmrc.agentsubscription.repository.EncryptionUtils.maybeDecryptOpt
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter

case class ContactEmailData(useBusinessEmail: Boolean, contactEmail: Option[String], encrypted: Option[Boolean] = None)

object ContactEmailData {
  def format(implicit crypto: Encrypter with Decrypter): Format[ContactEmailData] = {

    def reads(json: JsValue): JsResult[ContactEmailData] =
      for {
        isEncrypted <- (json \ "encrypted").validateOpt[Boolean]
        result = ContactEmailData(
                   (json \ "useBusinessEmail").as[Boolean],
                   maybeDecryptOpt("contactEmail", isEncrypted, json),
                   (json \ "encrypted").asOpt[Boolean]
                 )
      } yield result

    def writes(contactEmailData: ContactEmailData): JsValue =
      Json.obj(
        "useBusinessEmail" -> contactEmailData.useBusinessEmail,
        "contactEmail"     -> contactEmailData.contactEmail.map(stringEncrypter.writes),
        "encrypted"        -> Some(true)
      )

    Format(reads(_), contactEmailData => writes(contactEmailData))
  }
}
