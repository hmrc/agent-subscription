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

import org.joda.time.{ DateTime, DateTimeZone }
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.{ AmlsDetails, DateOfBirth }
import uk.gov.hmrc.domain.Nino

final case class SubscriptionJourneyRecord(
  internalId: String,
  identifyBusinessTask: IdentifyBusinessTask,
  amlsTask: AMLSTask,
  copyTask: CopyTask,
  createTask: CreateTask,
  updatedDateTime: LocalDateTime)

object SubscriptionJourneyRecord {
  implicit val format: OFormat[SubscriptionJourneyRecord] = Json.format
}

case class IdentifyBusinessTask(
  businessType: Option[BusinessType] = None,
  utr: Option[Utr] = None,
  postcode: Option[Postcode] = None,
  registration: Option[Registration] = None,
  nino: Option[Nino] = None,
  companyRegistrationNumber: Option[CompanyRegistrationNumber] = None,
  dateOfBirth: Option[DateOfBirth] = None,
  registeredForVat: Option[String] = None,
  vatDetails: Option[VatDetails] = None,
  completed: Boolean = false)

object IdentifyBusinessTask {
  implicit val format: OFormat[IdentifyBusinessTask] = Json.format
}

case class AMLSTask(
  checkAmls: Option[String] = None,
  amlsAppliedFor: Option[String] = None,
  amlsDetails: Option[AmlsDetails] = None,
  completed: Boolean = false)

object AMLSTask {
  implicit val format: OFormat[AMLSTask] = Json.format
}

case class CopyTask(
  toCopy: Seq[MappingUtrResult],
  completed: Boolean = false)

object CopyTask {
  implicit val format: OFormat[CopyTask] = Json.format
}

case class MappingUtrResult(
  internalId: String,
  utr: Utr,
  createdDate: DateTime = DateTime.now(DateTimeZone.UTC),
  cumulativeClientCount: List[Int] = List.empty)
object MappingUtrResult {
  implicit val format: OFormat[MappingUtrResult] = Json.format
}

case class CreateTask(
  internalId: String,
  completed: Boolean = false)

object CreateTask {
  implicit val format: OFormat[CreateTask] = Json.format
}
