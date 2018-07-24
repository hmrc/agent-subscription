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
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.Request
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription._
import uk.gov.hmrc.agentsubscription.audit.{AgentSubscriptionEvent, AuditService}
import uk.gov.hmrc.agentsubscription.connectors._
import uk.gov.hmrc.agentsubscription.model.{Agency, AgentRecord, KnownFacts, SubscriptionRequest, UpdateSubscriptionRequest}
import uk.gov.hmrc.agentsubscription.repository.RecoveryRepository
import uk.gov.hmrc.agentsubscription.utils.Retry
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}

import scala.concurrent.{ExecutionContext, Future}

private object SubscriptionAuditDetail {
  implicit val writes = Json.writes[SubscriptionAuditDetail]
}

private case class SubscriptionAuditDetail(
  agentReferenceNumber: Arn,
  utr: Utr,
  agencyName: String,
  agencyAddress: model.Address,
  agencyEmail: String,
  agencyTelephoneNumber: String)

case class EnrolmentAlreadyAllocated(message: String) extends Exception(message)

@Singleton
class SubscriptionService @Inject() (
  desConnector: DesConnector,
  taxEnrolmentsConnector: TaxEnrolmentsConnector,
  auditService: AuditService,
  recoveryRepository: RecoveryRepository) {

  private def desRequest(subscriptionRequest: SubscriptionRequest) = {
    val address = subscriptionRequest.agency.address
    DesSubscriptionRequest(
      agencyName = subscriptionRequest.agency.name,
      agencyEmail = subscriptionRequest.agency.email,
      telephoneNumber = subscriptionRequest.agency.telephone,
      agencyAddress = Address(
        address.addressLine1,
        address.addressLine2,
        address.addressLine3,
        address.addressLine4,
        address.postcode,
        address.countryCode))
  }

  def subscribeAgentToMtd(subscriptionRequest: SubscriptionRequest, authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Option[Arn]] = {

    desConnector.getRegistration(subscriptionRequest.utr) flatMap {
      case Some(DesRegistrationResponse(Some(desPostcode), isAnAsAgent, _, _, maybeArn)) if postcodesMatch(desPostcode, subscriptionRequest.knownFacts.postcode) => {
        subscribe(subscriptionRequest, authIds, isAnAsAgent, maybeArn)
      }
      case _ => Future successful None
    }
  }

  def updateSubscription(updateSubscriptionRequest: UpdateSubscriptionRequest, authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Option[Arn]] = {
    desConnector
      .getAgentRecordDetails(updateSubscriptionRequest.utr)
      .flatMap { agentRecord =>
        if (agentRecord.isAnASAgent) {
          val subscriptionRequest = requestForPartialSubscription(updateSubscriptionRequest, agentRecord)
          addKnownFactsAndEnrol(agentRecord.arn, subscriptionRequest, authIds)
            .map { _ =>
              auditService.auditEvent(
                AgentSubscriptionEvent.AgentSubscription,
                "Agent services subscription",
                auditDetailJsObject(agentRecord.arn, subscriptionRequest))
              Some(agentRecord.arn)
            }
        } else Future.successful(None)
      }.recover {
      case _: NotFoundException => None
    }
  }

  private def subscribe(
    subscriptionRequest: SubscriptionRequest,
    authIds: AuthIds,
    isAnAsAgent: Boolean,
    maybeArn: Option[Arn])(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Option[Arn]] = {
    for {
      arn <- maybeArn match {
        case Some(arn) if isAnAsAgent => Future.successful(arn)
        case _ => desConnector.subscribeToAgentServices(subscriptionRequest.utr, desRequest(subscriptionRequest))
      }
      _ <- addKnownFactsAndEnrol(arn, subscriptionRequest, authIds)
    } yield {
      auditService.auditEvent(AgentSubscriptionEvent.AgentSubscription, "Agent services subscription", auditDetailJsObject(arn, subscriptionRequest))
      Some(arn)
    }
  }

  private def auditDetailJsObject(arn: Arn, subscriptionRequest: SubscriptionRequest) =
    toJsObject(
      SubscriptionAuditDetail(
        arn,
        subscriptionRequest.utr,
        subscriptionRequest.agency.name,
        subscriptionRequest.agency.address,
        subscriptionRequest.agency.email,
        subscriptionRequest.agency.telephone))

  private def toJsObject(detail: SubscriptionAuditDetail): JsObject = Json.toJson(detail).as[JsObject]

  private def addKnownFactsAndEnrol(arn: Arn, subscriptionRequest: SubscriptionRequest, authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val tries = 3
    Retry.retry(tries)(
      taxEnrolmentsConnector.hasPrincipalGroupIds(arn).flatMap { alreadyEnrolled =>
        if (!alreadyEnrolled) {
          for {
            _ <- taxEnrolmentsConnector.deleteKnownFacts(arn)
            _ <- taxEnrolmentsConnector.sendKnownFacts(arn.value, subscriptionRequest.agency.address.postcode)
            enrolRequest = EnrolmentRequest(authIds.userId, "principal", subscriptionRequest.agency.name,
              Seq(KnownFact("AgencyPostcode", subscriptionRequest.agency.address.postcode)))
            _ <- taxEnrolmentsConnector.enrol(authIds.groupId, arn, enrolRequest)
          } yield ()
        } else {
          Future.failed(EnrolmentAlreadyAllocated("An enrolment for HMRC-AS-AGENT with this Arn as an identifier already exists"))
        }
      }).recover {
        case e: EnrolmentAlreadyAllocated => throw e
        case e =>
          Logger.error(s"Failed to add known facts and enrol for: ${arn.value} after $tries attempts", e)
          recoveryRepository.create(authIds, arn, subscriptionRequest, s"Failed to add known facts and enrol due to; ${e.getClass.getName}: ${e.getMessage}")
          throw new IllegalStateException(s"Failed to add known facts and enrol in EMAC for utr: ${subscriptionRequest.utr.value} and arn: ${arn.value}", e)
      }
  }

  /** This method creates a SubscriptionRequest for partially subscribed agents */
  private def requestForPartialSubscription(request: UpdateSubscriptionRequest, agentRecord: AgentRecord) = SubscriptionRequest(
    utr = request.utr,
    knownFacts = KnownFacts(postcode = agentRecord.knownfactPostcode),
    agency = Agency(
      name = agentRecord.agencyName,
      address = model.Address(
        addressLine1 = agentRecord.address.addressLine1,
        addressLine2 = agentRecord.address.addressLine2,
        addressLine3 = agentRecord.address.addressLine3,
        addressLine4 = agentRecord.address.addressLine4,
        postcode = agentRecord.address.postalCode,
        countryCode = agentRecord.address.countryCode),
      telephone = agentRecord.phoneNUmber.getOrElse(""),
      email = agentRecord.email))
}
