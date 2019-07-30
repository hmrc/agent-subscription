package uk.gov.hmrc.agentsubscription.controllers

import org.scalatest.concurrent.Eventually
import play.api.libs.json._
import play.api.libs.ws.WSClient
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.audit.{ AgentSubscriptionEvent, OverseasAgentSubscription }
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.{ AttemptingRegistration, Complete, Registered }
import uk.gov.hmrc.agentsubscription.model.{ EmailInformation, OverseasAmlsDetails, SafeId }
import uk.gov.hmrc.agentsubscription.stubs.DataStreamStub.{ writeAuditMergedSucceeds, writeAuditSucceeds }
import uk.gov.hmrc.agentsubscription.stubs._
import uk.gov.hmrc.agentsubscription.support.{ BaseAuditSpec, Resource }

class OverseasSubscriptionAuditingSpec extends BaseAuditSpec
  with Eventually with AuthStub with TaxEnrolmentsStubs
  with AgentAssuranceStub with OverseasDesStubs with AgentOverseasApplicationStubs with EmailStub {

  implicit val ws = app.injector.instanceOf[WSClient]

  private val arn = "TARN0000001"
  private val stubbedGroupId = "groupId"
  private val safeId = SafeId("XE0001234567890")
  private val safeIdJson = s"""{ "safeId": "${safeId.value}"}"""
  private val overseasAmlsDetails = OverseasAmlsDetails("supervisoryName", Some("supervisoryId"))
  val emailInfo = EmailInformation(
    Seq("agencyemail@domain.com"),
    "agent_services_account_created",
    Map("agencyName" -> "Agency name", "arn" -> "TARN0000001"))

  "creating an overseas subscription" should {
    "audit an OverseasAgentSubscription event" in {
      writeAuditMergedSucceeds()
      writeAuditSucceeds()

      requestIsAuthenticatedWithNoEnrolments()
      givenValidApplication("accepted")
      givenUpdateApplicationStatus(AttemptingRegistration, 204)
      organisationRegistrationSucceeds()
      givenUpdateApplicationStatus(Registered, 204, safeIdJson)
      subscriptionSucceeds(safeId.value, agencyDetailsJson)
      allocatedPrincipalEnrolmentNotExists(arn)
      deleteKnownFactsSucceeds(arn)
      createKnownFactsSucceeds(arn)
      enrolmentSucceeds(stubbedGroupId, arn)
      createOverseasAmlsSucceeds(Arn(arn), overseasAmlsDetails)
      givenUpdateApplicationStatus(Complete, 204, s"""{"arn" : "$arn"}""")
      givenEmailSent(emailInfo)

      val result = await(doOverseasSubscriptionRequest)

      result.status shouldBe 201

      DataStreamStub.verifyAuditRequestSent(
        OverseasAgentSubscription,
        expectedTags,
        expectedDetails)
    }
  }

  private def doOverseasSubscriptionRequest() = new Resource(s"/agent-subscription/overseas-subscription", port).putEmpty()

  private def expectedDetails: JsObject =
    Json.parse(
      s"""
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

  private def expectedTags: JsObject =
    Json.parse(
      s"""
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

