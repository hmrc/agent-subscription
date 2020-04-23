/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.{ Inject, Singleton }
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{ AnyContent, Request }
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription._
import uk.gov.hmrc.agentsubscription.audit.{ AgentSubscription, AuditService, OverseasAgentSubscription }
import uk.gov.hmrc.agentsubscription.auth.AuthActions.AuthIds
import uk.gov.hmrc.agentsubscription.connectors._
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.{ AttemptingRegistration, Complete, Registered }
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.repository.{ RecoveryRepository, SubscriptionJourneyRepository }
import uk.gov.hmrc.agentsubscription.utils.Retry
import uk.gov.hmrc.http.{ HeaderCarrier, NotFoundException }

import scala.concurrent.{ ExecutionContext, Future }

private object SubscriptionAuditDetail {
  implicit val writes: OWrites[SubscriptionAuditDetail] = Json.writes[SubscriptionAuditDetail]
}

private case class SubscriptionAuditDetail(
  agentReferenceNumber: Arn,
  utr: Utr,
  agencyName: String,
  agencyAddress: model.Address,
  agencyEmail: String,
  amlsDetails: Option[AmlsDetails])

case class OverseasSubscriptionAuditDetail(
  agentReferenceNumber: Option[Arn],
  safeId: SafeId,
  agencyName: String,
  agencyEmail: String,
  agencyAddress: OverseasAgencyAddress,
  amlsDetails: Option[OverseasAmlsDetails])

object OverseasSubscriptionAuditDetail {
  implicit val format: OFormat[OverseasSubscriptionAuditDetail] = Json.format[OverseasSubscriptionAuditDetail]
}

case class EnrolmentAlreadyAllocated(message: String) extends Exception(message)

@Singleton
class SubscriptionService @Inject() (
  desConnector: DesConnector,
  taxEnrolmentsConnector: TaxEnrolmentsConnector,
  auditService: AuditService,
  recoveryRepository: RecoveryRepository,
  subscriptionJourneyRepository: SubscriptionJourneyRepository,
  agentAssuranceConnector: AgentAssuranceConnector,
  agentOverseasApplicationConnector: AgentOverseasApplicationConnector,
  emailConnector: EmailConnector,
  mappingConnector: MappingConnector) {

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

  private def sendEmail(email: String, agencyName: String, arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    emailConnector.sendEmail(EmailInformation(Seq(email), "agent_services_account_created", Map("agencyName" -> agencyName, "arn" -> arn.value)))

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
            case _ => for {
              arn <- desConnector.subscribeToAgentServices(utr, desRequest(subscriptionRequest))
              _ <- mappingConnector.createMappings(arn)
              _ <- mappingConnector.createMappingDetails(arn)
              _ <- subscriptionJourneyRepository.delete(utr)
            } yield arn
          }
          updatedAmlsDetails <- agentAssuranceConnector.updateAmls(utr, arn)
          _ <- addKnownFactsAndEnrolUk(arn, subscriptionRequest, authIds)
          _ <- sendEmail(subscriptionRequest.agency.email, subscriptionRequest.agency.name, arn)
        } yield {
          auditService.auditEvent(AgentSubscription, "Agent services subscription", auditDetailJsObject(arn, subscriptionRequest, updatedAmlsDetails))
          Some(arn)
        }
      case _ =>
        Logger.warn(s"No business partner record was associated with $utr")
        Future successful None
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
            _ <- addKnownFactsAndEnrolUk(arn, subscriptionRequest, authIds)
          } yield {
            auditService.auditEvent(AgentSubscription, "Agent services subscription", auditDetailJsObject(arn, subscriptionRequest, updatedAmlsDetails))
            Some(arn)
          }
        } else Future.successful(None)
      }.recover {
        case _: NotFoundException => None
      }
  }

  def createOverseasSubscription(authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[AnyContent]): Future[Option[Arn]] = {
    val userId = authIds.userId

    agentOverseasApplicationConnector.currentApplication.flatMap {
      case CurrentApplication(AttemptingRegistration, _, _, _, _, _) =>
        Future.successful(None)
      case CurrentApplication(Registered | Complete, Some(safeId), amlsDetails, _, _, agencyDetails) =>
        subscribeAndEnrolOverseas(authIds, safeId, amlsDetails, maybeUkAgentSubscribing(agencyDetails))
      case application =>
        for {
          _ <- agentOverseasApplicationConnector.updateApplicationStatus(ApplicationStatus.AttemptingRegistration, userId)
          safeId <- desConnector.createOverseasBusinessPartnerRecord(OverseasRegistrationRequest(application))
          _ <- agentOverseasApplicationConnector.updateApplicationStatus(ApplicationStatus.Registered, userId, Some(safeId))
          arnOpt <- subscribeAndEnrolOverseas(authIds, safeId, application.amlsDetails, maybeUkAgentSubscribing(application.agencyDetails))
        } yield {
          val auditJson = Json.toJson(OverseasSubscriptionAuditDetail(
            arnOpt,
            safeId, application.agencyDetails.agencyName,
            application.agencyDetails.agencyEmail, application.agencyDetails.agencyAddress, application.amlsDetails)).as[JsObject]

          auditService.auditEvent(OverseasAgentSubscription, "Overseas agent subscription", auditJson)
          arnOpt
        }
    }
  }

  private def maybeUkAgentSubscribing(details: OverseasAgencyDetails): OverseasAgencyDetailsForMaybeUkAgent = {
    OverseasAgencyDetailsForMaybeUkAgent(agencyName = details.agencyName, agencyEmail = details.agencyEmail, agencyAddress = OverseasAddress.maybeUkAddress(OverseasBusinessAddress.fromOverseasAgencyAddress(details.agencyAddress)))
  }

  private def subscribeAndEnrolOverseas(authIds: AuthIds, safeId: SafeId, amlsDetailsOpt: Option[OverseasAmlsDetails], agencyDetails: OverseasAgencyDetailsForMaybeUkAgent)(implicit hc: HeaderCarrier, ec: ExecutionContext) =
    for {
      arn <- desConnector.subscribeToAgentServices(safeId, agencyDetails)
      _ <- addKnownFactsAndEnrolOverseas(arn, agencyDetails, authIds)
      _ <- amlsDetailsOpt match {
        case Some(amlsDetails) => agentAssuranceConnector.createOverseasAmls(arn, amlsDetails)
        case None => Future(())
      }
      _ <- agentOverseasApplicationConnector.updateApplicationStatus(ApplicationStatus.Complete, authIds.userId, None, Some(arn))
      _ <- sendEmail(agencyDetails.agencyEmail, agencyDetails.agencyName, arn)
    } yield Some(arn)

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

  private def addKnownFactsAndEnrolOverseas(arn: Arn, agencyDetails: OverseasAgencyDetailsForMaybeUkAgent, authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val knownFactKey = "CountryCode"
    val knownFactValue = agencyDetails.agencyAddress.countryCode
    val friendlyName = agencyDetails.agencyName

    addKnownFactsAndEnrol(arn, knownFactKey, knownFactValue, friendlyName, authIds)
  }

  private def addKnownFactsAndEnrolUk(arn: Arn, subscriptionRequest: SubscriptionRequest, authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val knownFactKey = "AgencyPostcode"
    val knownFactValue = subscriptionRequest.agency.address.postcode
    val friendlyName = subscriptionRequest.agency.name

    addKnownFactsAndEnrol(arn, knownFactKey, knownFactValue, friendlyName, authIds)
      .recover {
        case e: EnrolmentAlreadyAllocated => throw e
        case e: IllegalStateException =>
          recoveryRepository.create(authIds, arn, subscriptionRequest, s"Failed to add known facts and enrol due to; ${e.getCause.getClass.getName}: ${e.getCause.getMessage}")
          throw new IllegalStateException(s"Failed to add known facts and enrol in EMAC for utr: ${subscriptionRequest.utr.value} and arn: ${arn.value}", e)
      }
  }

  private def addKnownFactsAndEnrol(arn: Arn, knownFactKey: String, knownFactValue: String, friendlyName: String, authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val enrolRequest = EnrolmentRequest(
      userId = authIds.userId,
      `type` = "principal",
      friendlyName = friendlyName,
      Seq(KnownFact(knownFactKey, knownFactValue)))

    val tries = 3
    Retry.retry(tries)(
      taxEnrolmentsConnector.hasPrincipalGroupIds(arn).flatMap { alreadyEnrolled =>
        if (!alreadyEnrolled) {
          for {
            _ <- taxEnrolmentsConnector.deleteKnownFacts(arn)
            _ <- taxEnrolmentsConnector.addKnownFacts(arn.value, knownFactKey, knownFactValue)
            _ <- taxEnrolmentsConnector.enrol(authIds.groupId, arn, enrolRequest)
          } yield ()
        } else {
          Future.failed(EnrolmentAlreadyAllocated("An enrolment for HMRC-AS-AGENT with this Arn as an identifier already exists"))
        }
      }).recover {
        case e: EnrolmentAlreadyAllocated => throw e
        case e =>
          Logger.error(s"Failed to add known facts and enrol for: ${arn.value} after $tries attempts", e)
          throw new IllegalStateException(s"Failed to add known facts and enrol in EMAC for arn: ${arn.value}", e)
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
