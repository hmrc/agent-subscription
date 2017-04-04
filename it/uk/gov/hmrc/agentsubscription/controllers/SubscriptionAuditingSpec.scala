package uk.gov.hmrc.agentsubscription.controllers

import org.scalatest.concurrent.Eventually
import play.api.libs.json._
import uk.gov.hmrc.agentsubscription.model.SubscriptionRequest
import uk.gov.hmrc.agentsubscription.stubs.DataStreamStub.{writeAuditMergedSucceeds, writeAuditSucceeds}
import uk.gov.hmrc.agentsubscription.stubs.{AuthStub, DesStubs, GGAdminStubs, GGStubs}
import uk.gov.hmrc.agentsubscription.support.{BaseAuditSpec, Resource}

class SubscriptionAuditingSpec extends BaseAuditSpec with Eventually with DesStubs with AuthStub with GGStubs with GGAdminStubs{
  private val utr = "0123456789"

  "creating a subscription" should {
    import uk.gov.hmrc.agentsubscription.stubs.DataStreamStub

    "audit an AgentSubscription event" in {
      writeAuditMergedSucceeds()
      writeAuditSucceeds()

      requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
      organisationRegistrationExists(utr)
      subscriptionSucceeds(utr, Json.parse(subscriptionRequest(utr)).as[SubscriptionRequest])
      createKnownFactsSucceeds()
      enrolmentSucceeds()

      val result = await(doSubscriptionRequest(subscriptionRequest(utr)))

      result.status shouldBe 201

      eventually {
        DataStreamStub.verifyAuditRequestSent(
          expectedTags,
          expectedDetails(utr))
      }
    }
  }

  private def doSubscriptionRequest(request:String) = new Resource(s"/agent-subscription/subscription", port).postAsJson(request)

  private def subscriptionRequest(utr: String): String =
    s"""
       |{
       |  "utr": "$utr",
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

  private def expectedDetails(utr: String): JsObject =
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
         |  "agentRegistrationNumber": "ARN0001",
         |  "agencyEmail": "agency@example.com",
         |  "agencyTelephoneNumber": "0123 456 7890",
         |  "utr": "$utr"
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
