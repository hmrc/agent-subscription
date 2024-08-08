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

import play.api.libs.json.{Format, JsResult, JsSuccess, JsValue, Json}
import uk.gov.hmrc.agentsubscription.repository.EncryptionUtils.maybeDecryptOpt
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

case class ContactTradingNameData(hasTradingName: Boolean, contactTradingName: Option[String], encrypted: Option[Boolean] = None)

object ContactTradingNameData {
  def format(implicit crypto: Encrypter with Decrypter): Format[ContactTradingNameData] = {

    def reads(json: JsValue): JsResult[ContactTradingNameData] =
      for {
        isEncrypted <- (json \ "encrypted").validateOpt[Boolean]
        result = ContactTradingNameData(
                   (json \ "hasTradingName").as[Boolean],
                   maybeDecryptOpt("contactTradingName", isEncrypted, json),
                   isEncrypted
                 )
        } yield result

        def writes(contactTradingNameData: ContactTradingNameData): JsValue =
      Json.obj(
        "hasTradingName"    -> contactTradingNameData.hasTradingName,
        "contactTradingName" -> contactTradingNameData.contactTradingName.map(stringEncrypter.writes)
      )

    Format(reads(_), contactTradingNameData => writes(contactTradingNameData))
  }
}
