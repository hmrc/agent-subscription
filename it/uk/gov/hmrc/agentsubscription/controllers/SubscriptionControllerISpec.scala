package uk.gov.hmrc.agentsubscription.controllers

import play.api.libs.json._
import uk.gov.hmrc.agentsubscription.model.SubscriptionRequest
import uk.gov.hmrc.agentsubscription.stubs.DesStubs
import uk.gov.hmrc.agentsubscription.support.{BaseISpec, Resource}

class SubscriptionControllerISpec extends BaseISpec with DesStubs {
  private val utr = "0123456789"

  "creating a subscription" should {
    "return a response containing the ARN" when {
      "all fields are populated" in {
        subscriptionSucceeds(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])

        val result = await(doSubscriptionRequest())

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "ARN0001"
      }

      "addressLine3 and addressLine4 are missing" in {
        val fields = Seq(__ \ "address" \ "addressLine3", __ \ "address" \ "addressLine4")
        subscriptionSucceeds(utr, Json.parse(removeFields(fields)).as[SubscriptionRequest])

        val result = await(doSubscriptionRequest(removeFields(fields)))

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "ARN0001"
      }
    }

    "return Conflict if subscription exists" in {
      subscriptionAlreadyExists(utr)


      val result = await(doSubscriptionRequest())

      result.status shouldBe 409
    }

    "return Bad Request " when {
      "name is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(__ \ "name"))))

        result.status shouldBe 400
      }
      "address is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(__ \ "address"))))

        result.status shouldBe 400
      }
      "email is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(__ \ "email"))))

        result.status shouldBe 400
      }
      "telephone is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(__ \ "telephone"))))

        result.status shouldBe 400
      }

      "addressLine1 is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(__ \ "address" \ "addressLine1"))))

        result.status shouldBe 400
      }

      "addressLine2 is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(__ \ "address" \ "addressLine2"))))

        result.status shouldBe 400
      }

      "postcode is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(__ \ "address" \ "postcode"))))

        result.status shouldBe 400
      }

      "countryCode is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(__ \ "address" \ "countryCode"))))

        result.status shouldBe 400
      }
    }
  }

  private def doSubscriptionRequest(request:String = subscriptionRequest) = new Resource(s"/agent-subscription/subscription/$utr", port).postAsJson(request)

  private def removeFields(fields: Seq[JsPath]): String  = {
    val request = Json.parse(subscriptionRequest).as[JsObject]
    val filtered: JsObject = removeFields(request, fields)

    Json.stringify(filtered)
  }

  private def removeFields(jsObject: JsObject, fields: Seq[JsPath]): JsObject = {
    val transformer = fields.map(field => field.json.prune).reduce((a, b) => a andThen b)
    jsObject.transform(transformer).get
  }

  private val subscriptionRequest: String =
    s"""
       |{
       |  "name": "My Agency",
       |  "address": {
       |    "addressLine1": "Flat 1",
       |    "addressLine2": "1 Some Street",
       |    "addressLine3": "Anytown",
       |    "addressLine4": "County",
       |    "postcode": "AA1 1AA",
       |    "countryCode": "GB"
       |  },
       |  "email": "agency@example.com",
       |  "telephone": "0123 456 7890"
       |}
     """.stripMargin

}
