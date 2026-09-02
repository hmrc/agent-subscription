/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.Logging
import play.api.i18n.Lang
import play.api.libs.json._
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription._
import uk.gov.hmrc.agentsubscription.audit.AgentSubscription
import uk.gov.hmrc.agentsubscription.audit.AuditService
import uk.gov.hmrc.agentsubscription.audit.OverseasAgentSubscription
import uk.gov.hmrc.agentsubscription.auth.AuthActions.AuthIds
import uk.gov.hmrc.agentsubscription.connectors._
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.Complete
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.Registered
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.repository.SubscriptionJourneyRepository
import uk.gov.hmrc.agentsubscription.utils.Retry
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.mongo.lock.TimePeriodLockService

import scala.concurrent.duration._
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

private object SubscriptionAuditDetail {
  implicit val writes: OWrites[SubscriptionAuditDetail] = Json.writes[SubscriptionAuditDetail]
}

private case class SubscriptionAuditDetail(
  agentReferenceNumber: Arn,
  utr: Utr,
  agencyName: String,
  agencyAddress: model.Address,
  agencyEmail: String,
  amlsDetails: Option[AmlsDetails]
)

case class OverseasSubscriptionAuditDetail(
  agentReferenceNumber: Option[Arn],
  safeId: SafeId,
  agencyName: String,
  agencyEmail: String,
  agencyAddress: OverseasAgencyAddress,
  amlsDetails: Option[OverseasAmlsDetails]
)

object OverseasSubscriptionAuditDetail {
  implicit val format: OFormat[OverseasSubscriptionAuditDetail] = Json.format[OverseasSubscriptionAuditDetail]
}

case class EnrolmentAlreadyAllocated(message: String)
extends Exception(message)

@Singleton
class SubscriptionService @Inject() (
  desConnector: DesConnector,
  hipConnector: HipConnector,
  taxEnrolmentsConnector: TaxEnrolmentsConnector,
  auditService: AuditService,
  subscriptionJourneyRepository: SubscriptionJourneyRepository,
  agentAssuranceConnector: AgentAssuranceConnector,
  agentOverseasApplicationConnector: AgentOverseasApplicationConnector,
  emailConnector: EmailConnector,
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
extends Logging {

  private lazy val mongoLockRepository = new MongoLockRepository(mongoComponent, new CurrentTimestampSupport)

  private def withOverseasSubscriptionLock[T](userId: String)(body: => Future[Option[T]]): Future[Option[T]] = TimePeriodLockService(
    mongoLockRepository,
    lockId = s"create-overseas-subscription-$userId",
    ttl = 5.minutes
  ).withRenewedLock(body)
    .map {
      case Some(result) =>
        logger.info(s"Acquired lock for $userId")
        result
      case None =>
        logger.warn(s"Lock already held for $userId")
        None
    }

  private def sendEmail(
    email: String,
    agencyName: String,
    arn: Arn,
    langForEmail: Option[Lang]
  )(implicit
    rh: RequestHeader
  ): Future[Unit] = {
    val defaultTemplate = "agent_services_account_created" // english -- default and always for overseas agents
    val welshTemplate = "agent_services_account_created_cy" // welsh (for uk agents)
    val templateId: String =
      langForEmail.fold(defaultTemplate)(l =>
        if (l == Lang("cy"))
          welshTemplate
        else
          defaultTemplate
      )
    emailConnector.sendEmail(
      EmailInformation(
        Seq(email),
        templateId,
        Map("agencyName" -> agencyName, "arn" -> arn.value)
      )
    )
  }

  def createSubscription(
    subscriptionRequest: SubscriptionRequest,
    authIds: AuthIds
  )(implicit
    rh: RequestHeader
  ): Future[Option[Arn]] = {

    def subscribeAndMap(
      maybeArn: Option[Arn],
      safeId: SafeId,
      utr: Utr,
      isAnAsAgent: Boolean
    ): Future[Arn] =
      maybeArn match {
        case Some(arn) if isAnAsAgent => Future.successful(arn)
        case _ =>
          for {
            arn <- hipConnector.subscribeToAgentServicesUk(safeId, subscriptionRequest)
            _ <- subscriptionJourneyRepository.delete(utr.value)
          } yield arn
      }

    val utr = subscriptionRequest.utr
    desConnector.getRegistration(utr) flatMap {
      case Some(
            DesRegistrationResponse(
              isAnAsAgent,
              _,
              _,
              maybeArn,
              DesBusinessAddress(
                _,
                _,
                _,
                _,
                Some(desPostcode),
                _
              ),
              _,
              _,
              Some(safeId)
            )
          ) =>
        if (postcodesMatch(desPostcode, subscriptionRequest.knownFacts.postcode)) {
          //
          for {
            arn <- subscribeAndMap(
              maybeArn,
              SafeId(safeId),
              utr,
              isAnAsAgent
            )
            _ <- addKnownFactsAndEnrolUk(
              arn,
              subscriptionRequest,
              authIds
            )
            _ <- sendEmail(
              subscriptionRequest.agency.email,
              subscriptionRequest.agency.name,
              arn,
              subscriptionRequest.langForEmail
            )
          } yield {
            auditService.auditEvent(
              AgentSubscription,
              "Agent services subscription",
              auditDetailJsObject(
                arn,
                subscriptionRequest,
                subscriptionRequest.amlsDetails
              )
            )
            Some(arn)
          }
        }
        else {
          logger.warn(
            s"the postcode from the business partner record did not match that in the subscription request known facts"
          )
          Future successful None
        }
      case _ =>
        logger.warn(s"No business partner record was associated with $utr")
        Future successful None
    }
  }

  def updateSubscription(
    updateSubscriptionRequest: UpdateSubscriptionRequest,
    authIds: AuthIds
  )(implicit
    rh: RequestHeader
  ): Future[Option[Arn]] = desConnector
    .getAgentRecordDetails(updateSubscriptionRequest.utr)
    .flatMap { agentRecord =>
      if (
        agentRecord.isAnASAgent && postcodesMatch(
          agentRecord.businessPostcode,
          updateSubscriptionRequest.knownFacts.postcode
        )
      ) {
        val arn = agentRecord.arn
        val subscriptionRequest = mergeSubscriptionRequest(updateSubscriptionRequest, agentRecord)
        for {
          updatedAmlsDetails <- agentAssuranceConnector.updateAmls(updateSubscriptionRequest.utr, arn)
          _ <- addKnownFactsAndEnrolUk(
            arn,
            subscriptionRequest,
            authIds
          )
          _ <- sendEmail(
            subscriptionRequest.agency.email,
            subscriptionRequest.agency.name,
            arn,
            subscriptionRequest.langForEmail
          )
        } yield {
          auditService.auditEvent(
            AgentSubscription,
            "Agent services subscription",
            auditDetailJsObject(
              arn,
              subscriptionRequest,
              updatedAmlsDetails
            )
          )
          Some(arn)
        }
      }
      else
        Future.successful(None)
    }
    .recover { case _: NotFoundException => None }

  def createOverseasSubscription(
    authIds: AuthIds
  )(implicit rh: RequestHeader): Future[Option[Arn]] =
    withOverseasSubscriptionLock(authIds.userId) {
      agentOverseasApplicationConnector.currentApplication.flatMap {
        case CurrentApplication(
              Registered | Complete,
              Some(safeId),
              amlsDetails,
              _,
              _,
              agencyDetails
            ) =>
          subscribeAndEnrolOverseas(
            authIds,
            safeId,
            amlsDetails,
            agencyDetails
          )
        case application =>
          for {
            safeId <- desConnector.createOverseasBusinessPartnerRecord(OverseasRegistrationRequest(application))
            _ <- agentOverseasApplicationConnector
              .updateApplicationStatus(
                ApplicationStatus.Registered,
                authIds.userId,
                Some(safeId)
              )
            arnOpt <- subscribeAndEnrolOverseas(
              authIds,
              safeId,
              application.amlsDetails,
              application.agencyDetails
            )
          } yield {
            val auditJson = Json
              .toJson(
                OverseasSubscriptionAuditDetail(
                  arnOpt,
                  safeId,
                  application.agencyDetails.agencyName,
                  application.agencyDetails.agencyEmail,
                  application.agencyDetails.agencyAddress,
                  application.amlsDetails
                )
              )
              .as[JsObject]

            auditService.auditEvent(
              OverseasAgentSubscription,
              "Overseas agent subscription",
              auditJson
            )
            arnOpt
          }
      }
    }

  private def subscribeAndEnrolOverseas(
    authIds: AuthIds,
    safeId: SafeId,
    amlsDetailsOpt: Option[OverseasAmlsDetails],
    agencyDetails: OverseasAgencyDetails
  )(implicit rh: RequestHeader) =
    for {
      arn <- hipConnector.subscribeToAgentServicesOverseas(
        safeId,
        agencyDetails,
        amlsDetailsOpt
      )
      _ <- addKnownFactsAndEnrolOverseas(
        arn,
        agencyDetails,
        authIds
      )
      _ <- agentOverseasApplicationConnector
        .updateApplicationStatus(
          ApplicationStatus.Complete,
          authIds.userId,
          None,
          Some(arn)
        )
      _ <- sendEmail(
        agencyDetails.agencyEmail,
        agencyDetails.agencyName,
        arn,
        None
      )
    } yield Some(arn)

  private def auditDetailJsObject(
    arn: Arn,
    subscriptionRequest: SubscriptionRequest,
    updatedAmlsDetails: Option[AmlsDetails]
  ) = toJsObject(
    SubscriptionAuditDetail(
      arn,
      subscriptionRequest.utr,
      subscriptionRequest.agency.name,
      subscriptionRequest.agency.address,
      subscriptionRequest.agency.email,
      updatedAmlsDetails
    )
  )

  private def toJsObject(detail: SubscriptionAuditDetail): JsObject = Json.toJson(detail).as[JsObject]

  private def addKnownFactsAndEnrolOverseas(
    arn: Arn,
    agencyDetails: OverseasAgencyDetails,
    authIds: AuthIds
  )(implicit
    rh: RequestHeader
  ): Future[Unit] = {
    val knownFactKey = "CountryCode"
    val knownFactValue = agencyDetails.agencyAddress.countryCode
    val friendlyName = agencyDetails.agencyName

    addKnownFactsAndEnrol(
      arn,
      knownFactKey,
      knownFactValue,
      friendlyName,
      authIds
    )
  }

  private def addKnownFactsAndEnrolUk(
    arn: Arn,
    subscriptionRequest: SubscriptionRequest,
    authIds: AuthIds
  )(implicit
    rh: RequestHeader
  ): Future[Unit] = {
    val knownFactKey = "AgencyPostcode"
    val knownFactValue = subscriptionRequest.agency.address.postcode
    val friendlyName = subscriptionRequest.agency.name

    addKnownFactsAndEnrol(
      arn,
      knownFactKey,
      knownFactValue,
      friendlyName,
      authIds
    )
      .recover {
        case e: EnrolmentAlreadyAllocated => throw e
        case e: IllegalStateException =>
          throw new IllegalStateException(
            s"Failed to add known facts and enrol in EMAC for utr: ${subscriptionRequest.utr.value} and arn: ${arn.value}",
            e
          )
      }
  }

  private def addKnownFactsAndEnrol(
    arn: Arn,
    knownFactKey: String,
    knownFactValue: String,
    friendlyName: String,
    authIds: AuthIds
  )(implicit rh: RequestHeader): Future[Unit] = {
    val enrolRequest = EnrolmentRequest(
      userId = authIds.userId,
      `type` = "principal",
      friendlyName = friendlyName,
      Seq(KnownFact(knownFactKey, knownFactValue))
    )

    val tries = 3
    Retry
      .retry(tries)(taxEnrolmentsConnector.hasPrincipalGroupIds(arn).flatMap { alreadyEnrolled =>
        if (!alreadyEnrolled) {
          for {
            _ <- taxEnrolmentsConnector.deleteKnownFacts(arn)
            _ <- taxEnrolmentsConnector.addKnownFacts(
              arn.value,
              knownFactKey,
              knownFactValue
            )
            _ <- taxEnrolmentsConnector.enrol(
              authIds.groupId,
              arn,
              enrolRequest
            )
          } yield ()
        }
        else {
          Future.failed(
            EnrolmentAlreadyAllocated("An enrolment for HMRC-AS-AGENT with this Arn as an identifier already exists")
          )
        }
      })
      .recover {
        case e: EnrolmentAlreadyAllocated => throw e
        case e =>
          logger.error(s"Failed to add known facts and enrol for: ${arn.value} after $tries attempts", e)
          throw new IllegalStateException(s"Failed to add known facts and enrol in EMAC for arn: ${arn.value}", e)
      }
  }

  /** This method creates a SubscriptionRequest for partially subscribed agents */
  private def mergeSubscriptionRequest(
    request: UpdateSubscriptionRequest,
    agentRecord: AgentRecord
  ) = SubscriptionRequest(
    utr = request.utr,
    knownFacts = request.knownFacts,
    agency = Agency(
      name = agentRecord.agencyName,
      address = agentRecord.agencyAddress,
      telephone = agentRecord.phoneNumber,
      email = agentRecord.agencyEmail
    ),
    langForEmail = request.langForEmail,
    None
  )

}
