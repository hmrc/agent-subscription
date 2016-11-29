/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.controllers

import javax.inject._
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import play.api.mvc._
import scala.concurrent.Future
import play.api.libs.json.Json

@Singleton
class SandboxSubscriptionController @Inject()() extends BaseController {

  def createSubscription() = Action {
    Accepted.withHeaders(LOCATION -> routes.SandboxSubscriptionController.getSubscription("00010002-0003-0004-0005-000600070008").url)
  }

  def getSubscription(subscriptionId: String) = Action {
    Ok(Json.toJson(subscription(subscriptionId)))
  }

  private def subscription(subscriptionId: String): SubscriptionRequest =
    SubscriptionRequest(subscriptionId,
                          Some(Arn(subscriptionId)),
                          "DDCW Agency Ltd",
                          Address(Seq("1 Main St"), "Sometown", "A11 1AA"),
                          "0123 456 7890",
                          "me@example.com",
                          "mtd-sa",
                          "0123456789",
                          AgentCode("1234567890")
                        )

}