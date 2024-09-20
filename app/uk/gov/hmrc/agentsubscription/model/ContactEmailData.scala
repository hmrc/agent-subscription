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

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypterDecrypter
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

case class ContactEmailData(useBusinessEmail: Boolean, contactEmail: Option[String])

object ContactEmailData {
  implicit val format: OFormat[ContactEmailData] = Json.format
  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[ContactEmailData] =
    (
      (__ \ "useBusinessEmail").format[Boolean] and
        (__ \ "contactEmail").formatNullable[String](stringEncrypterDecrypter)
    )(ContactEmailData.apply, unlift(ContactEmailData.unapply))
}
