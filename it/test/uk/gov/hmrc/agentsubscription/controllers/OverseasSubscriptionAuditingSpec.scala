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

package uk.gov.hmrc.agentsubscription.controllers

import org.scalatest.concurrent.Eventually
import play.api.libs.json._
import play.api.libs.ws.WSClient
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.audit.OverseasAgentSubscription
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.AttemptingRegistration
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.Complete
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.Registered
import uk.gov.hmrc.agentsubscription.model.EmailInformation
import uk.gov.hmrc.agentsubscription.model.OverseasAmlsDetails
import uk.gov.hmrc.agentsubscription.model.SafeId
import uk.gov.hmrc.agentsubscription.stubs.DataStreamStub.writeAuditMergedSucceeds
import uk.gov.hmrc.agentsubscription.stubs.DataStreamStub.writeAuditSucceeds
import uk.gov.hmrc.agentsubscription.stubs._
import uk.gov.hmrc.agentsubscription.support.BaseAuditSpec
import uk.gov.hmrc.agentsubscription.support.Resource

class OverseasSubscriptionAuditingSpec
extends BaseAuditSpec
with Eventually
with AuthStub
with TaxEnrolmentsStubs
with AgentAssuranceStub
with OverseasDesStubs
with AgentOverseasApplicationStubs
with EmailStub {

  implicit val ws: WSClient = app.injector.instanceOf[WSClient]

  private val arn = "TARN0000001"
  private val stubbedGroupId = "groupId"
  private val safeId = SafeId("XE0001234567890")
  private val safeIdJson = s"""{ "safeId": "${safeId.value}"}"""
  private val overseasAmlsDetails = OverseasAmlsDetails("supervisoryName", Some("supervisoryId"))
  val emailInfo = EmailInformation(
    Seq("agencyemail@domain.com"),
    "agent_services_account_created",
    Map("agencyName" -> "Agency name", "arn" -> "TARN0000001")
  )

  "creating an overseas subscription" should {
    "audit an OverseasAgentSubscription event" in {
      writeAuditMergedSucceeds()
      writeAuditSucceeds()

      requestIsAuthenticatedWithNoEnrolments()
      givenValidApplication("accepted")
      givenUpdateApplicationStatus(AttemptingRegistration, 204)
      organisationRegistrationSucceeds()
      givenUpdateApplicationStatus(
        Registered,
        204,
        safeIdJson
      )
      subscriptionSucceeds(safeId.value, agencyDetailsJson)
      allocatedPrincipalEnrolmentNotExists(arn)
      deleteKnownFactsSucceeds(arn)
      createKnownFactsSucceeds(arn)
      enrolmentSucceeds(stubbedGroupId, arn)
      createOverseasAmlsSucceeds(Arn(arn), overseasAmlsDetails)
      givenUpdateApplicationStatus(
        Complete,
        204,
        s"""{"arn" : "$arn"}"""
      )
      givenEmailSent(emailInfo)

      val result = doOverseasSubscriptionRequest()

      result.status shouldBe 201

      DataStreamStub.verifyAuditRequestSent(
        OverseasAgentSubscription,
        expectedTags,
        expectedDetails
      )
    }
  }

  private def doOverseasSubscriptionRequest() = new Resource(s"/agent-subscription/overseas-subscription", port).putAsJson("")

  private def expectedDetails: JsObject = Json
    .parse(s"""
              |{
              |  "agencyName": "Agency name",
              |  "agencyEmail": "agencyemail@domain.com",
              |  "agencyAddress": {
              |    "addressLine1": "Mandatory Address Line 1",
              |    "addressLine2": "Mandatory Address Line 2",
              |    "countryCode": "IE"
              |  },
              |  "agentReferenceNumber": "$arn",
              |  "agencyEmail": "agencyemail@domain.com",
              |  "safeId": "${safeId.value}",
              |  "amlsDetails": {
              |      "supervisoryBody":"${overseasAmlsDetails.supervisoryBody}",
              |      "membershipNumber":"${overseasAmlsDetails.membershipNumber.get}"
              |  }
              |}
              |""".stripMargin)
    .asInstanceOf[JsObject]

  private def expectedTags: JsObject = Json
    .parse(s"""
              |{
              |  "path": "/agent-subscription/overseas-subscription",
              |  "transactionName": "Overseas agent subscription"
              |}
              |""".stripMargin)
    .asInstanceOf[JsObject]

  private val agencyDetailsJson =
    s"""
       |{
       |  "agencyName": "Agency name",
       |  "agencyEmail": "agencyemail@domain.com",
       |  "agencyAddress": {
       |    "addressLine1": "Mandatory Address Line 1",
       |    "addressLine2": "Mandatory Address Line 2",
       |    "countryCode": "IE"
       |  }
       |}
     """.stripMargin

}
