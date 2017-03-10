package uk.gov.hmrc.agentsubscription.controllers

import play.api.libs.json.Json.{stringify, toJson}
import play.api.libs.json._
import uk.gov.hmrc.agentsubscription.model.{KnownFacts, SubscriptionRequest}
import uk.gov.hmrc.agentsubscription.stubs.{AuthStub, DesStubs}
import uk.gov.hmrc.agentsubscription.support.{BaseISpec, Resource}

class SubscriptionControllerISpec extends BaseISpec with DesStubs with AuthStub {
  private val utr = "0123456789"

  "creating a subscription" should {
    val agency = __ \ "agency"
    val address = agency \ "address"
    "return a response containing the ARN" when {
      "all fields are populated" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        registrationExists(utr)
        subscriptionSucceeds(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])

        val result = await(doSubscriptionRequest())

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "ARN0001"
      }

      "addressLine2, addressLine3 and addressLine4 are missing" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        val fields = Seq(address \ "addressLine2", address \ "addressLine3", address \ "addressLine4")
        registrationExists(utr)
        subscriptionSucceeds(utr, Json.parse(removeFields(fields)).as[SubscriptionRequest])

        val result = await(doSubscriptionRequest(removeFields(fields)))

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "ARN0001"
      }
    }

    "return Conflict if subscription exists" in {
      requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
      registrationExists(utr)
      subscriptionAlreadyExists(utr)

      val result = await(doSubscriptionRequest())

      result.status shouldBe 409
    }

    "return forbidden" when {
      "no registration exists" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        registrationDoesNotExist(utr)

        val result = await(doSubscriptionRequest())

        result.status shouldBe 403
      }

      "postcodes don't match" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        registrationExists(utr)
        val request = Json.parse(subscriptionRequest).as[SubscriptionRequest].copy(knownFacts = KnownFacts("AA1 2AA"))

        val result = await(doSubscriptionRequest(stringify(toJson(request))))

        result.status shouldBe 403
      }

      "the user already has enrolments" in {
        requestIsAuthenticated().andIsAnAgent().andHasEnrolments()
        val result = await(doSubscriptionRequest())

        result.status shouldBe 403
      }
    }

    "return Bad Request " when {

      "utr is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(__ \ "utr"))))

        result.status shouldBe 400
      }

      "utr contains non-numeric characters" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((__, "utr", "ABCDE12345")))))

        result.status shouldBe 400
      }

      "utr contains fewer than 10 digits" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((__, "utr", "12345")))))

        result.status shouldBe 400
      }

      "utr contains more than 10 digits" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((__, "utr", "12345678901")))))

        result.status shouldBe 400
      }

      "name is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(agency \ "name"))))

        result.status shouldBe 400
      }
      "name is whitespace only" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((agency, "name", "    ")))))

        result.status shouldBe 400
      }
      "name is longer than 40 characters" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((agency, "name", "11111111111111111111111111111111111111111")))))

        result.status shouldBe 400
      }
      "address is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(address))))

        result.status shouldBe 400
      }
      "email is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(agency \ "email"))))

        result.status shouldBe 400
      }
      "email has no local part" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((agency, "email", "@domain")))))

        result.status shouldBe 400
      }
      "email has no domain part" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((agency, "email", "local@")))))

        result.status shouldBe 400
      }
      "email has no @" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((agency, "email", "local")))))

        result.status shouldBe 400
      }
      "telephone is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(agency \ "telephone"))))

        result.status shouldBe 400
      }
      "telephone is invalid" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((agency, "telephone", "012345")))))

        result.status shouldBe 400
      }

      "addressLine1 is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(address \ "addressLine1"))))

        result.status shouldBe 400
      }

      "addressLine1 contains only whitespace" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((address, "addressLine1", "   ")))))

        result.status shouldBe 400
      }

      "addressLine1 is longer than 35 characters" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((address, "addressLine1", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")))))

        result.status shouldBe 400
      }

      "addressLine2 is longer than 35 characters" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((address, "addressLine2", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")))))

        result.status shouldBe 400
      }
      "addressLine3 is longer than 35 characters" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((address, "addressLine3", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")))))

        result.status shouldBe 400
      }
      "addressLine4 is longer than 35 characters" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((address, "addressLine4", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")))))

        result.status shouldBe 400
      }

      "postcode is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(address \ "postcode"))))

        result.status shouldBe 400
      }

      "postcode is not valid" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((address, "postcode", "1234567")))))

        result.status shouldBe 400
      }

      "known facts postcode is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(__ \ "knownFacts" \ "postcode"))))

        result.status shouldBe 400
      }

      "known facts postcode is not valid" in {
        val result = await(doSubscriptionRequest(replaceFields(Seq((__ \ "knownFacts", "postcode", "1234567")))))

        result.status shouldBe 400
      }

      "countryCode is missing" in {
        val result = await(doSubscriptionRequest(removeFields(Seq(address \ "countryCode"))))

        result.status shouldBe 400
      }
    }
  }

  private def doSubscriptionRequest(request:String = subscriptionRequest) = new Resource(s"/agent-subscription/subscription", port).postAsJson(request)

  private def removeFields(fields: Seq[JsPath]): String  = {
    val request = Json.parse(subscriptionRequest).as[JsObject]
    val filtered: JsObject = removeFields(request, fields)

    stringify(filtered)
  }

  private def removeFields(jsObject: JsObject, fields: Seq[JsPath]): JsObject = {
    val transformer = fields.map(field => field.json.prune).reduce((a, b) => a andThen b)
    jsObject.transform(transformer).get
  }

  private def replaceFields(fields: Seq[(JsPath, String, String)]): String  = {
    val request = Json.parse(subscriptionRequest).as[JsObject]
    val filtered: JsObject = replaceFields(request, fields)

    println (stringify(filtered))
    stringify(filtered)
  }

  private def replaceFields(jsObject: JsObject, fields: Seq[(JsPath, String, String)]): JsObject = {
    val transformer = fields.map(field => field._1.json.update(
      __.read[JsObject].map(o => o ++ Json.obj(field._2 -> field._3))
    )).reduce((a, b) => a andThen b)
    jsObject.transform(transformer) match {
      case s: JsSuccess[JsObject] => s.get
      case e: JsError => println (e)
        throw new RuntimeException(s"Unable to transform JSON: $e")
    }
  }

  private val subscriptionRequest: String =
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

}
