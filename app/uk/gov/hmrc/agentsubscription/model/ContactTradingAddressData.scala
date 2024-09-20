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
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

case class ContactTradingAddressData(useBusinessAddress: Boolean, contactTradingAddress: Option[BusinessAddress])

object ContactTradingAddressData {
  implicit val format: OFormat[ContactTradingAddressData] = Json.format
  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[ContactTradingAddressData] =
    (
      (__ \ "useBusinessAddress").format[Boolean] and
        (__ \ "contactTradingAddress").formatNullable[BusinessAddress](BusinessAddress.databaseFormat(crypto))
    )(ContactTradingAddressData.apply, unlift(ContactTradingAddressData.unapply))
}
