/*
 * Copyright 2022 HM Revenue & Customs
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

/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.libs.json.{ Format, Json }
import uk.gov.hmrc.agentsubscription.connectors.BusinessAddress

case class Registration(
  taxpayerName: Option[String],
  isSubscribedToAgentServices: Boolean,
  isSubscribedToETMP: Boolean,
  address: BusinessAddress,
  emailAddress: Option[String],
  safeId: Option[String])

object Registration {
  implicit val formats: Format[Registration] = Json.format[Registration]
}

case class UpdateBusinessAddressForm(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  postCode: String)

object UpdateBusinessAddressForm {
  def apply(businessAddress: BusinessAddress): UpdateBusinessAddressForm =
    UpdateBusinessAddressForm(
      businessAddress.addressLine1,
      businessAddress.addressLine2,
      businessAddress.addressLine3,
      businessAddress.addressLine4,
      businessAddress.postalCode.getOrElse(throw new Exception("Postcode is mandatory")))
}

