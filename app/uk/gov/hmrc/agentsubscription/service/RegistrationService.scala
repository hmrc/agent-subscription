/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.service

import javax.inject.{Inject, Singleton}

import play.api.libs.json.{Json, _}
import uk.gov.hmrc.agentsubscription.audit.{AgentSubscriptionEvent, AuditService}
import uk.gov.hmrc.agentsubscription.auth.RequestWithAuthority
import uk.gov.hmrc.agentsubscription.connectors.{DesConnector, DesIndividual, DesRegistrationResponse}
import uk.gov.hmrc.agentsubscription.model.{Arn, RegistrationDetails}
import uk.gov.hmrc.agentsubscription.postcodesMatch
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future


private object CheckAgencyStatusAuditDetail {
  implicit val writes = Json.writes[CheckAgencyStatusAuditDetail]
}

private case class CheckAgencyStatusAuditDetail(
  authProviderId: Option[String],
  authProviderType: Option[String],
  utr: String,
  postcode: String,
  knownFactsMatched: Boolean,
  isSubscribedToAgentServices: Option[Boolean],
  agentReferenceNumber: Option[Arn]
)


@Singleton
class RegistrationService @Inject() (desConnector: DesConnector, auditService: AuditService) {

  def getRegistration(utr: String, postcode: String)(implicit hc: HeaderCarrier, request: RequestWithAuthority[Any]): Future[Option[RegistrationDetails]] =
    desConnector.getRegistration(utr) map {
      case Some(DesRegistrationResponse(Some(desPostcode), isAnASAgent, organisationName, None, agentReferenceNumber)) if postcodesMatch(desPostcode, postcode) =>
        auditCheckAgencyStatus(utr, postcode, knownFactsMatched = true, isSubscribedToAgentServices = Some(isAnASAgent), agentReferenceNumber)
        Some(RegistrationDetails(isAnASAgent, organisationName))
      case Some(DesRegistrationResponse(Some(desPostcode), isAnASAgent, _, Some(DesIndividual(first, last)), agentReferenceNumber)) if postcodesMatch(desPostcode, postcode) =>
        auditCheckAgencyStatus(utr, postcode, knownFactsMatched = true, isSubscribedToAgentServices = Some(isAnASAgent), agentReferenceNumber)
        Some(RegistrationDetails(isAnASAgent, Some(s"$first $last")))
      case _ =>
        auditCheckAgencyStatus(utr, postcode, knownFactsMatched = false, None, None)
        None
    }

  private def auditCheckAgencyStatus(utr: String, postcode: String, knownFactsMatched: Boolean, isSubscribedToAgentServices: Option[Boolean], agentReferenceNumber: Option[Arn])
    (implicit hc: HeaderCarrier, request: RequestWithAuthority[Any]): Unit =
    auditService.auditEvent(
      AgentSubscriptionEvent.CheckAgencyStatus,
      "Check agency status",
      toJsObject(CheckAgencyStatusAuditDetail(
        request.authority.authProviderId,
        request.authority.authProviderType,
        utr,
        postcode,
        knownFactsMatched,
        isSubscribedToAgentServices,
        agentReferenceNumber)))

  private def toJsObject(detail: CheckAgencyStatusAuditDetail): JsObject = Json.toJson(detail).as[JsObject]

}
