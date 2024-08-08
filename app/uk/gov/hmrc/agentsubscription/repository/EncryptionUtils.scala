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

package uk.gov.hmrc.agentsubscription.repository

import play.api.libs.json.{JsSuccess, JsValue}
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter}

object EncryptionUtils {
  def decrypt(fieldName: String, json: JsValue)(implicit crypto: Encrypter with Decrypter): String =
    (json \ fieldName).validate[String] match {
      case JsSuccess(value, _) => crypto.decrypt(Crypted(value)).value
      case _                   => throw new RuntimeException(s"Failed to decrypt $fieldName")
    }

  def decryptOpt(fieldName: String, json: JsValue)(implicit crypto: Encrypter with Decrypter): Option[String] =
    (json \ fieldName).validateOpt[String] match {
      case JsSuccess(value, _) => value.map { f: String => crypto.decrypt(Crypted(f)).value }
      case _                   => throw new RuntimeException(s"Failed to decrypt $fieldName")
    }

  def maybeDecrypt(fieldName: String, isEncrypted: Option[Boolean], json: JsValue)(implicit
    crypto: Encrypter with Decrypter
  ): String =
    isEncrypted match {
      case Some(true) => decrypt(fieldName, json)
      case _          => (json \ fieldName).as[String]
    }

  def maybeDecryptOpt(fieldName: String, isEncrypted: Option[Boolean], json: JsValue)(implicit
    crypto: Encrypter with Decrypter
  ): Option[String] =
    isEncrypted match {
      case Some(true) => decryptOpt(fieldName, json)
      case _          => (json \ fieldName).asOpt[String]
    }
}
