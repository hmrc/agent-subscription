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

package uk.gov.hmrc.agentsubscription.audit

import javax.inject.{ Inject, Singleton }

import play.api.libs.json.{ JsObject, JsString }
import play.api.mvc.Request
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class AuditService @Inject() (auditConnector: AuditConnector) {

  import AgentSubscriptionEvent.AgentSubscriptionEvent

  def auditEvent(
    event: AgentSubscriptionEvent,
    transactionName: String,
    extraDetail: JsObject)(implicit hc: HeaderCarrier, request: Request[Any]): Unit =
    send(createEvent(event, transactionName, extraDetail))

  private def createEvent(
    event: AgentSubscriptionEvent,
    transactionName: String,
    extraDetail: JsObject)(implicit hc: HeaderCarrier, request: Request[Any]) =
    ExtendedDataEvent(
      auditSource = "agent-subscription",
      auditType = event.toString,
      tags = hc.toAuditTags(transactionName, request.path),
      detail = toJsObject(hc.toAuditDetails()) ++ extraDetail)

  private[audit] def toJsObject(fields: Map[String, String]) =
    JsObject(fields.map { case (name, value) => (name, JsString(value)) })

  private def send(event: ExtendedDataEvent)(implicit hc: HeaderCarrier): Unit =
    auditConnector.sendExtendedEvent(event).map(_ => ())

}

object AgentSubscriptionEvent extends Enumeration {
  val AgentSubscription, CheckAgencyStatus = Value

  type AgentSubscriptionEvent = AgentSubscriptionEvent.Value
}
