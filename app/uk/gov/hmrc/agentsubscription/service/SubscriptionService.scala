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

import uk.gov.hmrc.agentsubscription.connectors.{Address, DesConnector, DesSubscriptionRequest}
import uk.gov.hmrc.agentsubscription.model.{Arn, SubscriptionRequest}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionService @Inject() (desConnector: DesConnector) {

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


  def subscribeAgentToMtd(subscriptionRequest: SubscriptionRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Arn]] = {
    desConnector.getRegistrationPostcode(subscriptionRequest.utr) flatMap {
        case Some(postcode) if postcodesMatch(postcode, subscriptionRequest) => desConnector.subscribeToAgentServices(subscriptionRequest.utr, desRequest(subscriptionRequest)).map (Some.apply)
        case _ => Future successful None
    }
  }

  private def postcodesMatch(postcode: String, subscriptionRequest: SubscriptionRequest) =
    postcode == subscriptionRequest.knownFacts.postcode
}
