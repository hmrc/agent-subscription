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
import uk.gov.hmrc.agentsubscription.model.SubscriptionRequest
import uk.gov.hmrc.agentsubscription.repository.RecoveryRepository
import uk.gov.hmrc.agentsubscription.utils.Retry

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

private object SubscriptionAuditDetail {
  implicit val writes = Json.writes[SubscriptionAuditDetail]
}

private case class SubscriptionAuditDetail (
  agentReferenceNumber: Arn,
  utr: Utr,
  agencyName: String,
  agencyAddress: model.Address,
  agencyEmail: String,
  agencyTelephoneNumber: String
)

@Singleton
class SubscriptionService @Inject() (
                                      desConnector: DesConnector,
                                      taxEnrolmentsConnector: TaxEnrolmentsConnector,
                                      auditService: AuditService,
                                      recoveryRepository: RecoveryRepository
                                    ) {

  private def desRequest(subscriptionRequest: SubscriptionRequest) = {
      val address = subscriptionRequest.agency.address
      DesSubscriptionRequest(
        agencyName = subscriptionRequest.agency.name,
        agencyEmail = subscriptionRequest.agency.email,
        telephoneNumber = subscriptionRequest.agency.telephone,
        agencyAddress = Address(address.addressLine1,
                                address.addressLine2,
                                address.addressLine3,
                                address.addressLine4,
                                address.postcode,
                                address.countryCode))
  }

  def subscribeAgentToMtd(subscriptionRequest: SubscriptionRequest, authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Option[Arn]] = {

    desConnector.getRegistration(subscriptionRequest.utr) flatMap {
        case Some(DesRegistrationResponse(Some(desPostcode), _, _, _, _))
          if postcodesMatch(desPostcode, subscriptionRequest.knownFacts.postcode) => {
          subscribe(subscriptionRequest, authIds)
        }
        case _ =>  Future successful None
    }
  }

  private def subscribe(subscriptionRequest: SubscriptionRequest, authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Option[Arn]] = {
    for {
      arn <- desConnector.subscribeToAgentServices(subscriptionRequest.utr, desRequest(subscriptionRequest))
      _ <- createKnownFacts(arn, subscriptionRequest, authIds)
      _ <- enrol(arn, subscriptionRequest, authIds)
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
        subscriptionRequest.agency.telephone
      )
    )

  private def createKnownFacts(arn: Arn, subscriptionRequest: SubscriptionRequest, authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    val tries = 3
    Retry.retry(tries)(
      taxEnrolmentsConnector.sendKnownFacts(arn.value, subscriptionRequest.agency.address.postcode)
    ).recover {
      case e =>
        Logger.error(s"Failed to create known facts for: ${arn.value} after $tries attempts", e)
        recoveryRepository.create(authIds, arn, subscriptionRequest, s"Failed to create known facts due to; ${e.getClass.getName}: ${e.getMessage}")
        throw new IllegalStateException(s"Failed to create known facts in EMAC for utr: ${subscriptionRequest.utr.value} and arn: ${arn.value}", e)
    }
  }

  private def enrol(arn: Arn, subscriptionRequest: SubscriptionRequest, authIds: AuthIds)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    val enrolRequest = EnrolmentRequest(authIds.userId,"principal",subscriptionRequest.agency.name,
      Seq(KnownFact("AgencyPostcode",subscriptionRequest.knownFacts.postcode)))

    val tries = 3
    Retry.retry(tries)(
      taxEnrolmentsConnector.enrol(authIds.groupId, arn, enrolRequest)
    ).recover {
      case e =>
        Logger.error(s"Failed to Enrol for: ${arn.value} after $tries attempts", e)
        recoveryRepository.create(authIds, arn, subscriptionRequest, s"Failed to Enrol due to; ${e.getClass.getName}: ${e.getMessage}")
        throw new IllegalStateException(s"Failed to create enrolment in EMAC for utr: ${subscriptionRequest.utr.value} and arn: ${arn.value}", e)
    }
  }

  private def toJsObject(detail: SubscriptionAuditDetail): JsObject = Json.toJson(detail).as[JsObject]
}
