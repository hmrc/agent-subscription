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

package uk.gov.hmrc.agentsubscription.service

import akka.japi
import javax.inject.{ Inject, Singleton }
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.Request
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription._
import uk.gov.hmrc.agentsubscription.audit.{ AgentSubscriptionEvent, AuditService }
import uk.gov.hmrc.agentsubscription.auth.AuthActions.AuthIds
import uk.gov.hmrc.agentsubscription.connectors._
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.{ AttemptingRegistration, Registered }
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.repository.RecoveryRepository
import uk.gov.hmrc.agentsubscription.utils.Retry
import uk.gov.hmrc.http.{ HeaderCarrier, NotFoundException }

import scala.concurrent.{ ExecutionContext, Future }

private object SubscriptionAuditDetail {
  implicit val writes = Json.writes[SubscriptionAuditDetail]
}

private case class SubscriptionAuditDetail(
  agentReferenceNumber: Arn,
  utr: Utr,
  agencyName: String,
  agencyAddress: model.Address,
  agencyEmail: String,
  amlsDetails: Option[AmlsDetails])

case class EnrolmentAlreadyAllocated(message: String) extends Exception(message)

@Singleton
class SubscriptionService @Inject() (
  desConnector: DesConnector,
  taxEnrolmentsConnector: TaxEnrolmentsConnector,
  auditService: AuditService,
  recoveryRepository: RecoveryRepository,
  agentAssuranceConnector: AgentAssuranceConnector,
  agentOverseasApplicationConnector: AgentOverseasApplicationConnector) {

  private def desRequest(subscriptionRequest: SubscriptionRequest) = {
    val address = subscriptionRequest.agency.address
    DesSubscriptionRequest(
      agencyName = subscriptionRequest.agency.name,
      agencyEmail = subscriptionRequest.agency.email,
      telephoneNumber = subscriptionRequest.agency.telephone,
      agencyAddress = connectors.Address(
        address.addressLine1,
        address.addressLine2,
        address.addressLine3,
        address.addressLine4,
        address.postcode,
        address.countryCode))
  }

  def createSubscription(subscriptionRequest: SubscriptionRequest, authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Option[Arn]] = {
    val utr = subscriptionRequest.utr
    desConnector.getRegistration(utr) flatMap {
      case Some(DesRegistrationResponse(isAnAsAgent, _, _, maybeArn, BusinessAddress(_, _, _, _, Some(desPostcode), _), _)) if postcodesMatch(desPostcode, subscriptionRequest.knownFacts.postcode) =>
        for {
          _ <- subscriptionRequest.amlsDetails match {
            case Some(details) => agentAssuranceConnector.createAmls(utr, details)
            case None => Future.successful(false)
          }
          arn <- maybeArn match {
            case Some(arn) if isAnAsAgent => Future.successful(arn)
            case _ => desConnector.subscribeToAgentServices(utr, desRequest(subscriptionRequest))
          }
          updatedAmlsDetails <- agentAssuranceConnector.updateAmls(utr, arn)
          _ <- addKnownFactsAndEnrol(arn, subscriptionRequest, authIds)
        } yield {
          auditService.auditEvent(AgentSubscriptionEvent.AgentSubscription, "Agent services subscription", auditDetailJsObject(arn, subscriptionRequest, updatedAmlsDetails))
          Some(arn)
        }
      case _ => Future successful None
    }
  }

  def updateSubscription(updateSubscriptionRequest: UpdateSubscriptionRequest, authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Option[Arn]] = {
    desConnector
      .getAgentRecordDetails(updateSubscriptionRequest.utr)
      .flatMap { agentRecord =>
        if (agentRecord.isAnASAgent && postcodesMatch(agentRecord.businessPostcode, updateSubscriptionRequest.knownFacts.postcode)) {
          val arn = agentRecord.arn
          val subscriptionRequest = mergeSubscriptionRequest(updateSubscriptionRequest, agentRecord)
          for {
            updatedAmlsDetails <- agentAssuranceConnector.updateAmls(updateSubscriptionRequest.utr, arn)
            _ <- addKnownFactsAndEnrol(arn, subscriptionRequest, authIds)
          } yield {
            auditService.auditEvent(AgentSubscriptionEvent.AgentSubscription, "Agent services subscription", auditDetailJsObject(arn, subscriptionRequest, updatedAmlsDetails))
            Some(arn)
          }
        } else Future.successful(None)
      }.recover {
        case _: NotFoundException => None
      }
  }

  def createOverseasSubscription(subscriptionRequest: OverseasSubscriptionRequest, userId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Option[Arn]] = {

    def subscribeToDes(safeId: SafeId) =
      for {
        arn <- desConnector.subscribeToAgentServices(safeId, subscriptionRequest)
        _ <- agentOverseasApplicationConnector.updateApplicationStatus(ApplicationStatus.Complete, userId)
      } yield Some(arn)

    agentOverseasApplicationConnector.currentApplicationStatus.flatMap {
      case CurrentApplicationStatus(AttemptingRegistration, _) =>
        Future.successful(None)
      case CurrentApplicationStatus(Registered, Some(safeId)) =>
        subscribeToDes(safeId)
      case _ =>
        for {
          _ <- agentOverseasApplicationConnector.updateApplicationStatus(ApplicationStatus.AttemptingRegistration, userId)
          safeId <- desConnector.createOverseasBusinessPartnerRecord(subscriptionRequest.toRegistrationRequest)
          _ <- agentOverseasApplicationConnector.updateApplicationStatus(ApplicationStatus.Registered, userId, Some(safeId))
          arnOpt <- subscribeToDes(safeId)
        } yield arnOpt
    }
  }

  private def auditDetailJsObject(arn: Arn, subscriptionRequest: SubscriptionRequest, updatedAmlsDetails: Option[AmlsDetails]) =
    toJsObject(
      SubscriptionAuditDetail(
        arn,
        subscriptionRequest.utr,
        subscriptionRequest.agency.name,
        subscriptionRequest.agency.address,
        subscriptionRequest.agency.email,
        updatedAmlsDetails))

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
  private def mergeSubscriptionRequest(request: UpdateSubscriptionRequest, agentRecord: AgentRecord) = SubscriptionRequest(
    utr = request.utr,
    knownFacts = request.knownFacts,
    agency = Agency(
      name = agentRecord.agencyName,
      address = agentRecord.agencyAddress,
      telephone = agentRecord.phoneNumber,
      email = agentRecord.agencyEmail),
    None)
}
