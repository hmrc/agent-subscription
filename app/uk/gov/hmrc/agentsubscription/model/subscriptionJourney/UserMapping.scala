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

import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.agentsubscription.model.AuthProviderId
import uk.gov.hmrc.domain.AgentCode

/**
 * A single GG user (agent login) which is being consolidated into a new ASA account
 *
 * @param authProviderId identifies the GG user being mapped
 * @param agentCode the main agent code that this GG user has - part of auth details
 * @param legacyEnrolments the applicable enrolments for this agent
 * @param count the number of active client relationships - from EACD
 * @param ggTag the user's label for this GG user, generally the last 4 digits of the GG ID
 */
final case class UserMapping(
  authProviderId: AuthProviderId,
  agentCode: Option[AgentCode],
  legacyEnrolments: Seq[AgentEnrolment],
  count: Int = 0,
  ggTag: String)

object UserMapping {
  implicit val format: OFormat[UserMapping] = Json.format
}

final case class AgentEnrolment(enrolmentType: LegacyAgentEnrolmentType, identifierValue: IdentifierValue)

object AgentEnrolment {
  implicit val format: OFormat[AgentEnrolment] = Json.format
}
