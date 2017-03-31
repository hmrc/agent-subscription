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

import play.api.libs.json._
import play.api.mvc.Request
import uk.gov.hmrc.agentsubscription._
import uk.gov.hmrc.agentsubscription.audit.{AgentSubscriptionEvent, AuditService}
import uk.gov.hmrc.agentsubscription.connectors._
import uk.gov.hmrc.agentsubscription.model.{Agency, Arn, SubscriptionRequest}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

private object SubscriptionAuditDetail {
  implicit val writes = Json.writes[SubscriptionAuditDetail]
}

private case class SubscriptionAuditDetail(
  agentRegistrationNumber: Arn,
  utr: String,
  agencyName: String,
  agencyAddress: model.Address,
  agencyEmail: String,
  agencyTelephoneNumber: String
)

@Singleton
class SubscriptionService @Inject() (
  desConnector: DesConnector,
  governmentGatewayAdminConnector: GovernmentGatewayAdminConnector,
  governmentGatewayConnector: GovernmentGatewayConnector,
  auditService: AuditService) {

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

  def subscribeAgentToMtd(subscriptionRequest: SubscriptionRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Option[Arn]] = {
    desConnector.getRegistration(subscriptionRequest.utr) flatMap {
        case Some(DesRegistrationResponse(Some(desPostcode), _, _, _))
          if postcodesMatch(desPostcode, subscriptionRequest.knownFacts.postcode) => subscribe(subscriptionRequest)
        case _ => Future successful None
    }
  }

  private def subscribe(subscriptionRequest: SubscriptionRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Option[Arn]] =
    for {
      arn <- desConnector.subscribeToAgentServices(subscriptionRequest.utr, desRequest(subscriptionRequest))
      _ <- createKnownFacts(arn, subscriptionRequest)
      _ <- enrol(arn, subscriptionRequest)
      _ <- auditService.auditEvent(AgentSubscriptionEvent.AgentSubscription, "Agent services subscription", auditDetailJsObject(arn, subscriptionRequest))
    } yield {
      Some(arn)
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

  private def createKnownFacts(arn: Arn, subscriptionRequest: SubscriptionRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext) =
    governmentGatewayAdminConnector.createKnownFacts(arn.arn, subscriptionRequest.knownFacts.postcode) recover {
      case e => throw new IllegalStateException(s"Failed to create known facts in GG for utr: ${subscriptionRequest.utr} and arn: ${arn.arn}", e)
    }

  private def enrol(arn: Arn, subscriptionRequest: SubscriptionRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext) =
    governmentGatewayConnector.enrol(subscriptionRequest.agency.name, arn.arn, subscriptionRequest.knownFacts.postcode) recover {
      case e => throw new IllegalStateException(s"Failed to create enrolment in GG for utr: ${subscriptionRequest.utr} and arn: ${arn.arn}", e)
    }

  private def toJsObject(detail: SubscriptionAuditDetail): JsObject = Json.toJson(detail).as[JsObject]
}
