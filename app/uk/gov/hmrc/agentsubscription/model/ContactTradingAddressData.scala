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

import play.api.libs.json._
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

case class ContactTradingAddressData(useBusinessAddress: Boolean, contactTradingAddress: Option[BusinessAddress])

object ContactTradingAddressData {
  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[ContactTradingAddressData] = {

    def reads(json: JsValue): JsResult[ContactTradingAddressData] =
      for {
        useBusinessAddress <- (json \ "useBusinessAddress").validate[Boolean]
        contactTradingAddress <-
          (json \ "contactTradingAddress").validateOpt[BusinessAddress](BusinessAddress.databaseFormat(crypto))
      } yield ContactTradingAddressData(useBusinessAddress, contactTradingAddress)

    def writes(contactTradingAddressData: ContactTradingAddressData): JsValue =
      Json.obj(
        "useBusinessAddress" -> contactTradingAddressData.useBusinessAddress,
        "contactTradingAddress" -> contactTradingAddressData.contactTradingAddress.map(
          BusinessAddress.databaseFormat.writes
        )
      )

    Format(reads(_), contactTradingAddressData => writes(contactTradingAddressData))
  }
  implicit val writes: Writes[ContactTradingAddressData] = Json.writes[ContactTradingAddressData]
}
