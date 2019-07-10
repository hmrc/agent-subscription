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

package uk.gov.hmrc.agentsubscription.model.subscriptionJourneyRepositoryModel

import java.time.LocalDateTime

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.DateOfBirth
import uk.gov.hmrc.domain.Nino

final case class SubscriptionJourneyRecord(
                                            internalId: String,
                                            returnId: String,
                                            businessDetails: BusinessDetails,
                                            amlsDetails: AmlsDetails,
                                            mappingDetails: MappingDetails,
                                            cleanCredsCreated: Boolean,
                                            updatedDateTime: LocalDateTime)

object SubscriptionJourneyRecord {
  implicit val format: OFormat[SubscriptionJourneyRecord] = Json.format
}

case class BusinessDetails(
                            businessType: Option[BusinessType] = None,
                            utr: Option[Utr] = None,
                            postcode: Option[Postcode] = None,
                            registration: Option[Registration] = None,
                            nino: Option[Nino] = None,
                            companyRegistrationNumber: Option[CompanyRegistrationNumber] = None,
                            dateOfBirth: Option[DateOfBirth] = None,
                            registeredForVat: Option[String] = None,
                            vatDetails: Option[VatDetails] = None)

object BusinessDetails {
  implicit val format: OFormat[BusinessDetails] = Json.format
}

case class AmlsDetails(
                        checkAmls: Option[String] = None,
                        amlsAppliedFor: Option[String] = None,
                        amlsDetails: Option[AmlsDetails] = None)

object AmlsDetails {
  implicit val format: OFormat[AmlsDetails] = Json.format
}

case class MappingDetails(
                     toCopy: Seq[MappingUtrResult],
                     completed: Boolean = false)

object MappingDetails {
  implicit val format: OFormat[MappingDetails] = Json.format
}

case class MappingUtrResult(
                             internalId: String,
                             agentCodes: Seq[String] = Seq.empty,
                             count: Int = 0
                           )

object MappingUtrResult {
  implicit val format: OFormat[MappingUtrResult] = Json.format
}
