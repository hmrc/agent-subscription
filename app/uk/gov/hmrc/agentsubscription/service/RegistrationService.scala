/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.audit.{AgentSubscriptionEvent, AuditService}
import uk.gov.hmrc.agentsubscription.connectors._
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
  isAnAsAgentInDes: Option[Boolean],
  agentReferenceNumber: Option[Arn]
)


@Singleton
class RegistrationService @Inject() (desConnector: DesConnector, taxEnrolmentsConnector: TaxEnrolmentsConnector, auditService: AuditService) {
  protected def getLogger: LoggerLike = Logger

  def getRegistration(utr: Utr, postcode: String)(implicit hc: HeaderCarrier, provider: Provider, request: Request[AnyContent]): Future[Option[RegistrationDetails]] = {
    desConnector.getRegistration(utr) flatMap {
      case Some(DesRegistrationResponse(Some(desPostcode), isAnASAgent, organisationName, None, agentReferenceNumber)) =>
        if (isAnASAgent) {
          getLogger.warn(s"The business partner record of type organisation associated with $utr is already subscribed with arn $agentReferenceNumber and a postcode was returned")
        }

        checkRegistrationAndEnrolment(utr, postcode, desPostcode, isAnASAgent, organisationName, agentReferenceNumber)
      case Some(DesRegistrationResponse(Some(desPostcode), isAnASAgent, _, Some(DesIndividual(first, last)), agentReferenceNumber)) =>
        if (isAnASAgent) {
          getLogger.warn(s"The business partner record of type individual associated with $utr is already subscribed with arn $agentReferenceNumber and a postcode was returned")
        }

        checkRegistrationAndEnrolment(utr, postcode, desPostcode, isAnASAgent, Some(s"$first $last"), agentReferenceNumber)
      case Some(DesRegistrationResponse(postCode, isAnASAgent, _, _, agentReferenceNumber)) =>
        if (isAnASAgent) {
          getLogger.warn(s"The business partner record associated with $utr is already subscribed with arn $agentReferenceNumber with postcode: ${postCode.nonEmpty}")
          auditCheckAgencyStatus(utr, postcode, knownFactsMatched = false, Some(true), Some(isAnASAgent), agentReferenceNumber)
        } else {
          getLogger.warn(s"The business partner record associated with $utr is not subscribed with postcode: ${postCode.nonEmpty}")
          auditCheckAgencyStatus(utr, postcode, knownFactsMatched = false, None, Some(isAnASAgent), None)
        }
        Future.successful(None)
      case None =>
        getLogger.warn(s"No business partner record was associated with $utr")
        auditCheckAgencyStatus(utr, postcode, knownFactsMatched = false, None, None, None)
        Future.successful(None)
    }
  }

  private def checkRegistrationAndEnrolment(utr: Utr, postcode: String, desPostcode: String,
                                            isAnASAgent: Boolean, taxpayerName: Option[String],
                                            maybeArn: Option[Arn])
                                           (implicit hc: HeaderCarrier, provider: Provider, request: Request[AnyContent]): Future[Option[RegistrationDetails]] = {
    val knownFactsMatched = postcodesMatch(desPostcode, postcode)

    if (knownFactsMatched) {
      val isSubscribedToAgentServices = maybeArn match {
        case Some(arn) if isAnASAgent =>
          taxEnrolmentsConnector.hasPrincipalGroupIds(arn).map(_ && isAnASAgent)
        case _ =>
          Future.successful(false)
      }

      isSubscribedToAgentServices.map { x =>
        auditCheckAgencyStatus(utr, postcode, knownFactsMatched, isSubscribedToAgentServices = Some(x), Some(isAnASAgent), maybeArn)
        Some(RegistrationDetails(x, taxpayerName))
      }

    } else {
      auditCheckAgencyStatus(utr, postcode, knownFactsMatched, None, Some(isAnASAgent), None)
      Future.successful(None)
    }
  }

  private def auditCheckAgencyStatus(utr: Utr, postcode: String, knownFactsMatched: Boolean, isSubscribedToAgentServices: Option[Boolean], isAnAsAgentInDes: Option[Boolean], agentReferenceNumber: Option[Arn])
                                    (implicit hc: HeaderCarrier, provider: Provider, request: Request[AnyContent]): Unit =
    auditService.auditEvent(
      AgentSubscriptionEvent.CheckAgencyStatus,
      "Check agency status",
      toJsObject(CheckAgencyStatusAuditDetail(
        Some(provider.providerId),
        Some(provider.providerType),
        utr,
        postcode,
        knownFactsMatched,
        isSubscribedToAgentServices,
        isAnAsAgentInDes,
        agentReferenceNumber)))

  private def toJsObject(detail: CheckAgencyStatusAuditDetail): JsObject = Json.toJson(detail).as[JsObject]

}
