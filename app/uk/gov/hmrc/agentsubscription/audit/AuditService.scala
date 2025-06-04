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

package uk.gov.hmrc.agentsubscription.audit

import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentsubscription.utils.RequestSupport.hc
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class AuditService @Inject() (auditConnector: AuditConnector)(implicit val ec: ExecutionContext) {

  def auditEvent(
    event: AgentSubscriptionEvent,
    transactionName: String,
    extraDetail: JsObject
  )(implicit
    rh: RequestHeader
  ): Unit = send(createEvent(
    event,
    transactionName,
    extraDetail
  ))

  private def createEvent(
    event: AgentSubscriptionEvent,
    transactionName: String,
    extraDetail: JsObject
  )(implicit
    rh: RequestHeader
  ) = ExtendedDataEvent(
    auditSource = "agent-subscription",
    auditType = event.toString,
    tags = hc.toAuditTags(transactionName, rh.path),
    detail = toJsObject(hc.toAuditDetails()) ++ extraDetail
  )

  private[audit] def toJsObject(fields: Map[String, String]) = JsObject(fields.map { case (name, value) => (name, JsString(value)) })

  private def send(event: ExtendedDataEvent)(implicit rh: RequestHeader): Unit = {
    auditConnector.sendExtendedEvent(event).map(_ => ())
    ()
  }

}

sealed abstract class AgentSubscriptionEvent
case object AgentSubscription
extends AgentSubscriptionEvent
case object CheckAgencyStatus
extends AgentSubscriptionEvent
case object OverseasAgentSubscription
extends AgentSubscriptionEvent
case object CompaniesHouseOfficerCheck
extends AgentSubscriptionEvent
case object CompaniesHouseStatusCheck
extends AgentSubscriptionEvent
