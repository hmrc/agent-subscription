package uk.gov.hmrc.agentsubscription.controllers

import org.scalatest.concurrent.Eventually
import play.api.libs.json._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.SubscriptionRequest
import uk.gov.hmrc.agentsubscription.stubs.DataStreamStub.{ writeAuditMergedSucceeds, writeAuditSucceeds }
import uk.gov.hmrc.agentsubscription.stubs.{ AuthStub, DesStubs, TaxEnrolmentsStubs }
import uk.gov.hmrc.agentsubscription.support.{ BaseAuditSpec, Resource }

class SubscriptionAuditingSpec extends BaseAuditSpec with Eventually with DesStubs with AuthStub with TaxEnrolmentsStubs {
  private val utr = Utr("7000000002")

  val arn = "ARN0001"
  val groupId = "groupId"

  "creating a subscription" should {
    import uk.gov.hmrc.agentsubscription.audit.AgentSubscriptionEvent
    import uk.gov.hmrc.agentsubscription.stubs.DataStreamStub

    "audit an AgentSubscription event" in {
      writeAuditMergedSucceeds()
      writeAuditSucceeds()

      requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
      organisationRegistrationExists(utr, isAnASAgent = false, arn = arn)
      subscriptionSucceeds(utr, Json.parse(subscriptionRequest(utr)).as[SubscriptionRequest])
      allocatedPrincipalEnrolmentNotExists(arn)
      deleteKnownFactsSucceeds(arn)
      createKnownFactsSucceeds(arn)
      enrolmentSucceeds(groupId, arn)

      val result = await(doSubscriptionRequest(subscriptionRequest(utr)))

      result.status shouldBe 201

      eventually {
        DataStreamStub.verifyAuditRequestSent(
          AgentSubscriptionEvent.AgentSubscription,
          expectedTags,
          expectedDetails(utr))
      }
    }
  }

  private def doSubscriptionRequest(request: String) = new Resource(s"/agent-subscription/subscription", port).postAsJson(request)

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
         |  "agentReferenceNumber": "ARN0001",
         |  "agencyEmail": "agency@example.com",
         |  "agencyTelephoneNumber": "0123 456 7890",
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
