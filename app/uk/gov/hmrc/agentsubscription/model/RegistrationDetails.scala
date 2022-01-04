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

package uk.gov.hmrc.agentsubscription.model

import play.api.libs.json.Json.format
import play.api.libs.json.OFormat
import uk.gov.hmrc.agentsubscription.connectors.BusinessAddress

case class RegistrationDetails(
  isSubscribedToAgentServices: Boolean,
  isSubscribedToETMP: Boolean,
  taxpayerName: Option[String],
  address: BusinessAddress,
  emailAddress: Option[String],
  safeId: Option[String] = None)

object RegistrationDetails {
  implicit val registrationDetailsObjectFormat: OFormat[RegistrationDetails] = format
}
