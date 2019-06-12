package uk.gov.hmrc.agentsubscription.controllers

import java.time.LocalDate

import org.scalatest.concurrent.Eventually
import play.api.libs.json._
import play.api.libs.ws.WSClient
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription.model.{ AmlsDetails, EmailInformation, SubscriptionRequest }
import uk.gov.hmrc.agentsubscription.stubs.DataStreamStub.{ writeAuditMergedSucceeds, writeAuditSucceeds }
import uk.gov.hmrc.agentsubscription.stubs._
import uk.gov.hmrc.agentsubscription.support.{ BaseAuditSpec, Resource }
import uk.gov.hmrc.agentsubscription.audit.AgentSubscriptionEvent

class SubscriptionAuditingSpec extends BaseAuditSpec with Eventually with DesStubs with AuthStub with TaxEnrolmentsStubs with AgentAssuranceStub with EmailStub {
  private val utr = Utr("7000000002")

  val arn = "TARN0000001"
  val groupId = "groupId"
  implicit val ws = app.injector.instanceOf[WSClient]

  val amlsDetails: AmlsDetails = AmlsDetails("supervisory", "12345", LocalDate.now())
  val emailInfo = EmailInformation(
    Seq("agency@example.com"),
    "agent_services_account_created",
    Map("agencyName" -> "My Agency", "arn" -> "TARN0000001"))

  "creating a subscription" should {
    "audit an AgentSubscription event" in {
      writeAuditMergedSucceeds()
      writeAuditSucceeds()

      requestIsAuthenticatedWithNoEnrolments()
      organisationRegistrationExists(utr, isAnASAgent = false, arn = arn)
      subscriptionSucceeds(utr, Json.parse(subscriptionRequest(utr)).as[SubscriptionRequest])
      allocatedPrincipalEnrolmentNotExists(arn)
      deleteKnownFactsSucceeds(arn)
      createKnownFactsSucceeds(arn)
      enrolmentSucceeds(groupId, arn)
      createAmlsSucceeds(utr, amlsDetails)
      updateAmlsSucceeds(utr, Arn(arn), amlsDetails)
      givenEmailSent(emailInfo)

      val result = await(doSubscriptionRequest(subscriptionRequest(utr)))

      result.status shouldBe 201

      DataStreamStub.verifyAuditRequestSent(
        AgentSubscriptionEvent.AgentSubscription,
        expectedTags,
        expectedDetails(utr))
    }
  }

  "updating a partial subscription" should {
    "audit an AgentSubscription event" in {
      writeAuditMergedSucceeds()
      writeAuditSucceeds()

      requestIsAuthenticatedWithNoEnrolments()
      agentRecordExists(utr, true, arn)
      allocatedPrincipalEnrolmentNotExists(arn)
      deleteKnownFactsSucceeds(arn)
      createKnownFactsSucceeds(arn)
      enrolmentSucceeds(groupId, arn)
      createAmlsSucceeds(utr, amlsDetails)
      updateAmlsSucceeds(utr, Arn(arn), amlsDetails)

      val result = await(doUpdateSubscriptionRequest(updateSubscriptionRequest))

      result.status shouldBe 200

      eventually {
        DataStreamStub.verifyAuditRequestSent(
          AgentSubscriptionEvent.AgentSubscription,
          expectedTags,
          expectedDetailsForUpdateSubscription(utr))
      }
    }
  }

  private def doSubscriptionRequest(request: String) = new Resource(s"/agent-subscription/subscription", port).postAsJson(request)
  private def doUpdateSubscriptionRequest(request: String = updateSubscriptionRequest) = new Resource(s"/agent-subscription/subscription", port).putAsJson(request)

  private def subscriptionRequest(utr: Utr): String =
    s"""
       |{
       |  "utr": "${utr.value}",
       |  "knownFacts": {
       |    "postcode": "AA1 1AA"
       |  },
       |  "agency": {
       |    "name": "My Agency",
       |    "address": {
       |      "addressLine1": "Flat 1",
       |      "addressLine2": "1 Some Street",
       |      "addressLine3": "Anytown",
       |      "addressLine4": "County",
       |      "postcode": "AA1 2AA",
       |      "countryCode": "GB"
       |    },
       |    "email": "agency@example.com",
       |    "telephone": "0123 456 7890"
       |  },
       |  "amlsDetails": {
       |      "supervisoryBody":"supervisory",
       |      "membershipNumber":"12345",
       |      "membershipExpiresOn":"${LocalDate.now()}"
       |  }
       |}
     """.stripMargin

  private val updateSubscriptionRequest =
    s"""
       |{
       |  "utr": "${utr.value}",
       |  "knownFacts": {
       |    "postcode": "TF3 4ER"
       |  }
       |}
    """.stripMargin

  private def expectedDetails(utr: Utr): JsObject =
    Json.parse(
      s"""
         |{
         |  "agencyName": "My Agency",
         |  "agencyAddress": {
         |     "addressLine1": "Flat 1",
         |     "addressLine2": "1 Some Street",
         |     "addressLine3": "Anytown",
         |     "addressLine4": "County",
         |     "postcode": "AA1 2AA",
         |     "countryCode": "GB"
         |  },
         |  "agentReferenceNumber": "TARN0000001",
         |  "agencyEmail": "agency@example.com",
         |  "utr": "${utr.value}",
         |  "amlsDetails": {
         |      "supervisoryBody":"supervisory",
         |      "membershipNumber":"12345",
         |      "membershipExpiresOn":"${LocalDate.now()}"
         |  }
         |}
         |""".stripMargin)
      .asInstanceOf[JsObject]

  private def expectedDetailsForUpdateSubscription(utr: Utr): JsObject =
    Json.parse(
      s"""
         |{
         |  "agencyName": "My Agency",
         |  "agencyAddress": {
         |     "addressLine1": "Flat 1",
         |     "addressLine2": "1 Some Street",
         |     "addressLine3": "Anytown",
         |     "addressLine4": "County",
         |     "postcode": "AA1 2AA",
         |     "countryCode": "GB"
         |  },
         |  "agentReferenceNumber": "TARN0000001",
         |  "agencyEmail": "agency@example.com",
         |  "utr": "${utr.value}"
         |}
         |""".stripMargin)
      .asInstanceOf[JsObject]

  private def expectedTags: JsObject =
    Json.parse(
      s"""
         |{
         |  "path": "/agent-subscription/subscription",
         |  "transactionName": "Agent services subscription"
         |}
         |""".stripMargin)
      .asInstanceOf[JsObject]

}
