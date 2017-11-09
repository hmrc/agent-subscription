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

import play.api.{Logger, LoggerLike}
import play.api.libs.json.{Json, _}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.audit.{AgentSubscriptionEvent, AuditService}
import uk.gov.hmrc.agentsubscription.auth.RequestWithAuthority
import uk.gov.hmrc.agentsubscription.connectors.{DesConnector, DesIndividual, DesRegistrationResponse}
import uk.gov.hmrc.agentsubscription.model.RegistrationDetails
import uk.gov.hmrc.agentsubscription.postcodesMatch
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier


private object CheckAgencyStatusAuditDetail {
  implicit val writes = Json.writes[CheckAgencyStatusAuditDetail]
}

private case class CheckAgencyStatusAuditDetail(
  authProviderId: Option[String],
  authProviderType: Option[String],
  utr: Utr,
  postcode: String,
  knownFactsMatched: Boolean,
  isSubscribedToAgentServices: Option[Boolean],
  agentReferenceNumber: Option[Arn]
)


@Singleton
class RegistrationService @Inject() (desConnector: DesConnector, auditService: AuditService) {
  protected def getLogger: LoggerLike = Logger

  def getRegistration(utr: Utr, postcode: String)(implicit hc: HeaderCarrier, request: RequestWithAuthority[Any]): Future[Option[RegistrationDetails]] =
    desConnector.getRegistration(utr) map {
      case Some(DesRegistrationResponse(Some(desPostcode), isAnASAgent, organisationName, None, agentReferenceNumber)) =>
        if (isAnASAgent) {
          getLogger.warn(s"The business partner record of type organisation associated with $utr is already subscribed with arn $agentReferenceNumber and a postcode was returned")
        }

        if (postcodesMatch(desPostcode, postcode)) {
          auditCheckAgencyStatus(utr, postcode, knownFactsMatched = true, isSubscribedToAgentServices = Some(isAnASAgent), agentReferenceNumber)
          Some(RegistrationDetails(isAnASAgent, organisationName))
        } else {
          auditCheckAgencyStatus(utr, postcode, knownFactsMatched = false, None, None)
          None
        }
      case Some(DesRegistrationResponse(Some(desPostcode), isAnASAgent, _, Some(DesIndividual(first, last)), agentReferenceNumber)) =>
        if (isAnASAgent) {
          getLogger.warn(s"The business partner record of type individual associated with $utr is already subscribed with arn $agentReferenceNumber and a postcode was returned")
        }

        if (postcodesMatch(desPostcode, postcode)) {
          auditCheckAgencyStatus(utr, postcode, knownFactsMatched = true, isSubscribedToAgentServices = Some(isAnASAgent), agentReferenceNumber)
          Some(RegistrationDetails(isAnASAgent, Some(s"$first $last")))
        } else {
          auditCheckAgencyStatus(utr, postcode, knownFactsMatched = false, None, None)
          None
        }
      case Some(DesRegistrationResponse(postCode, isAnASAgent, _, _, agentReferenceNumber))  =>
        if (isAnASAgent){
          getLogger.warn(s"The business partner record associated with $utr is already subscribed with arn $agentReferenceNumber with postcode: ${postCode.nonEmpty}")
          auditCheckAgencyStatus(utr, postcode, knownFactsMatched = false, Some(true), agentReferenceNumber)
        }
        else {
          getLogger.warn(s"The business partner record associated with $utr is not subscribed with postcode: ${postCode.nonEmpty}")
          auditCheckAgencyStatus(utr, postcode, knownFactsMatched = false, None, None)
        }

        None
      case None =>
        getLogger.warn(s"No business partner record was associated with $utr")
        auditCheckAgencyStatus(utr, postcode, knownFactsMatched = false, None, None)
        None
    }

  private def auditCheckAgencyStatus(utr: Utr, postcode: String, knownFactsMatched: Boolean, isSubscribedToAgentServices: Option[Boolean], agentReferenceNumber: Option[Arn])
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
