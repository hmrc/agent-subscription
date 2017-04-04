package uk.gov.hmrc.agentsubscription.controllers

import play.api.libs.json._
import uk.gov.hmrc.agentsubscription.model.SubscriptionRequest
import uk.gov.hmrc.agentsubscription.stubs.{AuthStub, DesStubs, GGAdminStubs, GGStubs}
import uk.gov.hmrc.agentsubscription.support.{BaseAuditSpec, Resource}

class SubscriptionControllerAuditSpec extends BaseAuditSpec with DesStubs with AuthStub with GGStubs with GGAdminStubs{
  private val utr = "0123456789"

  "auditing" should {
    import uk.gov.hmrc.agentsubscription.audit.AgentSubscriptionEvent
    import uk.gov.hmrc.agentsubscription.stubs.DataStreamStub

    val agency = __ \ "agency"
    val address = agency \ "address"
    "report a response containing the ARN" when {
      "all fields are populated" in {
        DataStreamStub.auditingMergedRequest()
        DataStreamStub.auditingRequest()

        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        organisationRegistrationExists(utr)
        subscriptionSucceeds(utr, Json.parse(subscriptionRequest(utr)).as[SubscriptionRequest])
        createKnownFactsSucceeds()
        enrolmentSucceeds()

        val result = await(doSubscriptionRequest(subscriptionRequest(utr)))

        result.status shouldBe 201

        DataStreamStub.verifyAuditRequestSent(
          AgentSubscriptionEvent.AgentSubscription,
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
         |    "clientIP" : "-",
         |    "path" : "/agent-subscription/subscription",
         |    "X-Session-ID" : "-",
         |    "Akamai-Reputation" : "-",
         |    "X-Request-ID" : "-",
         |    "clientPort" : "-",
         |    "transactionName" : "Agent services subscription"
         |}
         |""".stripMargin)
      .asInstanceOf[JsObject]

}
