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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json.{stringify, toJson}
import play.api.libs.json._
import play.api.libs.ws.WSClient
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.stubs._
import uk.gov.hmrc.agentsubscription.support.{BaseISpec, Resource}

import java.time.LocalDate

class SubscriptionControllerISpec
    extends BaseISpec with DesStubs with AuthStub with TaxEnrolmentsStubs with AgentAssuranceStub with EmailStub
    with MappingStubs {
  val utr = Utr("7000000002")

  val arn = "TARN0000001"
  val groupId = "groupId"
  implicit val ws = app.injector.instanceOf[WSClient]

  val amlsDetails: AmlsDetails = AmlsDetails(
    "supervisory",
    membershipNumber = Some("12345"),
    appliedOn = None,
    membershipExpiresOn = Some(LocalDate.now()),
    amlsSafeId = Some("amlsSafeId"),
    agentBPRSafeId = Some("agentBPRSafeId")
  )

  val emailInfo = EmailInformation(
    Seq("agency@example.com"),
    "agent_services_account_created",
    Map("agencyName" -> "My Agency", "arn" -> "TARN0000001")
  )

  "creating a subscription" should {
    val agency = __ \ "agency"
    val address = agency \ "address"
    val invalidAddress = "Invalid road %@"

    class TestSetup {
      requestIsAuthenticatedWithNoEnrolments()
      organisationRegistrationExists(utr, isAnASAgent = false, arn = arn)
      createAmlsSucceeds(utr, amlsDetails)
      subscriptionSucceeds(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])
      allocatedPrincipalEnrolmentNotExists(arn)
      deleteKnownFactsSucceeds(arn)
      createKnownFactsSucceeds(arn)
      enrolmentSucceeds(groupId, arn)
      updateAmlsSucceeds(utr, Arn(arn), amlsDetails)
      givenMappingCreationWithStatus(Arn(arn), 201)
      givenEmailSent(emailInfo)
    }

    "return a response containing the ARN" when {
      "all fields are populated" in new TestSetup {
        val result = doSubscriptionRequest()

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "TARN0000001"

        verify(1, postRequestedFor(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
        verify(1, postRequestedFor(urlEqualTo(enrolmentUrl(groupId, arn))))
      }

      "addressLine2, addressLine3 and addressLine4 are missing" in new TestSetup {
        val fields = Seq(address \ "addressLine2", address \ "addressLine3", address \ "addressLine4")
        subscriptionSucceeds(utr, Json.parse(removeFields(fields)).as[SubscriptionRequest])

        val result = doSubscriptionRequest(removeFields(fields))

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "TARN0000001"

        verify(1, postRequestedFor(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
        verify(1, postRequestedFor(urlEqualTo(enrolmentUrl(groupId, arn))))
      }

      "BPR has isAnAsAgent=true and there is no previous allocation for HMRC-AS-AGENT for the arn" in new TestSetup {
        organisationRegistrationExists(utr, isAnASAgent = true, arn = arn)

        val result = doSubscriptionRequest()

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "TARN0000001"

        verify(0, postRequestedFor(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
        verify(1, postRequestedFor(urlEqualTo(enrolmentUrl(groupId, arn))))
      }

      "all fields except telephone number are populated" in new TestSetup {
        subscriptionSucceedsWithoutTelephoneNo(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])

        val result = doSubscriptionRequest(subscriptionRequestWithoutTelephoneNo)

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "TARN0000001"

        verify(1, postRequestedFor(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
        verify(1, postRequestedFor(urlEqualTo(enrolmentUrl(groupId, arn))))
      }
    }

    "return Conflict if already subscribed (both ETMP has isAnAsAgent=true and there is an existing HMRC-AS-AGENT enrolment for their Arn)" in new TestSetup {
      organisationRegistrationExists(utr, isAnASAgent = true, arn = arn)
      allocatedPrincipalEnrolmentExists(arn, "someGroupId")

      val result = doSubscriptionRequest()

      result.status shouldBe 409

      verify(0, deleteRequestedFor(urlEqualTo(s"$deleteKnownFactsUrl$arn")))
      verify(0, putRequestedFor(urlEqualTo(s"$createKnownFactsUrl$arn")))
      verify(0, postRequestedFor(urlEqualTo(enrolmentUrl("groupId", arn))))
    }

    "return forbidden" when {
      "no registration exists" in {
        requestIsAuthenticatedWithNoEnrolments()
        registrationDoesNotExist(utr)

        val result = doSubscriptionRequest()

        result.status shouldBe 403
      }

      "postcodes don't match" in {
        requestIsAuthenticatedWithNoEnrolments()
        organisationRegistrationExists(utr)
        val request = Json.parse(subscriptionRequest).as[SubscriptionRequest].copy(knownFacts = KnownFacts("AA1 2AA"))

        val result = doSubscriptionRequest(stringify(toJson(request)))

        result.status shouldBe 403
      }

      "the user already has enrolments" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest()

        result.status shouldBe 403
      }
    }

    "return Bad Request " when {
      "utr is missing" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(removeFields(Seq(__ \ "utr")))

        result.status shouldBe 400
      }

      "utr contains non-numeric characters" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((__, "utr", "ABCDE12345"))))

        result.status shouldBe 400
      }

      "utr contains fewer than 10 digits" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((__, "utr", "12345"))))

        result.status shouldBe 400
      }

      "utr contains more than 10 digits" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((__, "utr", "12345678901"))))

        result.status shouldBe 400
      }

      "name contains invalid characters" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((agency, "name", "InvalidAgencyName!@"))))

        result.status shouldBe 400
      }

      "address is missing" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(removeFields(Seq(address)))

        result.status shouldBe 400
      }

      "address line 1 contains invalid characters" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((address, "addressLine1", invalidAddress))))

        result.status shouldBe 400
      }

      "address line 2 contains invalid characters" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((address, "addressLine2", invalidAddress))))
        result.status shouldBe 400
      }

      "address line 3 contains invalid characters" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((address, "addressLine3", invalidAddress))))
        result.status shouldBe 400
      }

      "address line 4 contains invalid characters" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((address, "addressLine4", invalidAddress))))
        result.status shouldBe 400
      }

      "email is missing" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(removeFields(Seq(agency \ "email")))

        result.status shouldBe 400
      }
      "email has no local part" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((agency, "email", "@domain"))))

        result.status shouldBe 400
      }
      "email has no domain part" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((agency, "email", "local@"))))

        result.status shouldBe 400
      }
      "email has no @" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((agency, "email", "local"))))

        result.status shouldBe 400
      }

      "telephone number contains words" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((agency, "telephone", "0123 456 78aa"))))

        result.status shouldBe 400
      }

      "telephone number is provided but empty" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((agency, "telephone", ""))))

        result.status shouldBe 400
      }

      "postcode is missing" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(removeFields(Seq(address \ "postcode")))

        result.status shouldBe 400
      }

      "known facts postcode is not valid" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(replaceFields(Seq((__ \ "knownFacts", "postcode", "1234567"))))

        result.status shouldBe 400
      }

      "countryCode is missing" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doSubscriptionRequest(removeFields(Seq(address \ "countryCode")))

        result.status shouldBe 400
      }

      "store amls fails with 400 error from agent assurance" in {

        requestIsAuthenticatedWithNoEnrolments()
        organisationRegistrationExists(utr, isAnASAgent = false, arn = arn)
        createAmlsFailsWithStatus(400)

        val result = doSubscriptionRequest()

        result.status shouldBe 400
      }

      "create amls succeeds but update amls fails with 400 error from agent assurance" in new TestSetup {
        updateAmlsFailsWithStatus(utr, Arn(arn), 400)

        val result = doSubscriptionRequest()

        result.status shouldBe 400
      }
    }

    "throw a 500 error if " when {
      "DES API #1173 Subscribe to Agent Services fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        registrationRequestFails()

        val result = doSubscriptionRequest()

        result.status shouldBe 500
      }

      "query allocated enrolment fails in EMAC " in {
        requestIsAuthenticatedWithNoEnrolments()
        organisationRegistrationExists(utr, isAnASAgent = true, arn = arn)
        createAmlsSucceeds(utr, amlsDetails)
        subscriptionSucceeds(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])
        updateAmlsSucceeds(utr, Arn(arn), amlsDetails)
        allocatedPrincipalEnrolmentFails(arn)

        val result = doSubscriptionRequest()

        result.status shouldBe 500
      }

      "delete known facts fails in EMAC " in {
        requestIsAuthenticatedWithNoEnrolments()
        organisationRegistrationExists(utr, isAnASAgent = false, arn = arn)
        createAmlsSucceeds(utr, amlsDetails)
        subscriptionSucceeds(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])
        updateAmlsSucceeds(utr, Arn(arn), amlsDetails)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsFails("")
        givenMappingCreationWithStatus(Arn(arn), 201)

        val result = doSubscriptionRequest()

        result.status shouldBe 500
      }

      "create known facts fails in EMAC " in {
        requestIsAuthenticatedWithNoEnrolments()
        organisationRegistrationExists(utr, isAnASAgent = false, arn = arn)
        createAmlsSucceeds(utr, amlsDetails)
        subscriptionSucceeds(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])
        updateAmlsSucceeds(utr, Arn(arn), amlsDetails)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds("")
        createKnownFactsFails("")
        givenMappingCreationWithStatus(Arn(arn), 201)

        val result = doSubscriptionRequest()

        result.status shouldBe 500
      }

      "create enrolment fails in EMAC " in new TestSetup {
        enrolmentFails(groupId, arn)

        val result = doSubscriptionRequest()

        result.status shouldBe 500
      }
    }
  }

  "updating a partial subscription" should {
    "return a response containing the ARN a valid utr is given as input and when the user is not enrolled in EMAC but is subscribed in ETMP" when {
      "with full DES-GetAgentRecord" in {
        testPartialSubscriptionWith(agentRecordExists(utr, true, arn))
      }

      "the DES-GetAgentRecord does not contain a telephone number" in {
        testPartialSubscriptionWith(agentRecordExistsWithoutPhoneNumber(utr, true, arn))
      }

      "DES-GetAgentRecord does not contain contact details" in {
        testPartialSubscriptionWith(agentRecordExistsWithoutContactDetails(utr, true, arn))
      }

      def testPartialSubscriptionWith[A](givenAgentRecord: => A) = {
        requestIsAuthenticatedWithNoEnrolments()
        givenAgentRecord
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(groupId, arn)
        createAmlsSucceeds(utr, amlsDetails)
        updateAmlsSucceeds(utr, Arn(arn), amlsDetails)

        val result = doUpdateSubscriptionRequest()

        result.status shouldBe 200
        (result.json \ "arn").as[String] shouldBe "TARN0000001"

        verify(1, getRequestedFor(urlEqualTo(s"/registration/personal-details/utr/${utr.value}")))
        verify(1, postRequestedFor(urlEqualTo(enrolmentUrl(groupId, arn))))
      }
    }

    "return Conflict if already subscribed (both ETMP has isAsAgent=true and there is an existing HMRC-AS-AGENT enrolment for their Arn)" in {
      requestIsAuthenticatedWithNoEnrolments()
      agentRecordExists(utr, true, arn)
      allocatedPrincipalEnrolmentExists(arn, "someGroupId")
      createAmlsSucceeds(utr, amlsDetails)
      updateAmlsSucceeds(utr, Arn(arn), amlsDetails)

      val result = doUpdateSubscriptionRequest()

      result.status shouldBe 409

      verify(0, deleteRequestedFor(urlEqualTo(s"$deleteKnownFactsUrl$arn")))
      verify(0, putRequestedFor(urlEqualTo(s"$createKnownFactsUrl$arn")))
      verify(0, postRequestedFor(urlEqualTo(enrolmentUrl("groupId", arn))))
    }

    "return forbidden" when {
      "no registration exists" in {
        requestIsAuthenticatedWithNoEnrolments()
        agentRecordDoesNotExist(utr)

        val result = doUpdateSubscriptionRequest()

        result.status shouldBe 403
      }

      "postcodes don't match" in {
        requestIsAuthenticatedWithNoEnrolments()
        agentRecordExists(utr)
        val request =
          Json.parse(updateSubscriptionRequest).as[UpdateSubscriptionRequest].copy(knownFacts = KnownFacts("AA1 2AA"))

        val result = doUpdateSubscriptionRequest(stringify(toJson(request)))

        result.status shouldBe 403
      }

      "the user already has enrolments" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doUpdateSubscriptionRequest()

        result.status shouldBe 403
      }
    }

    "return Bad Request " when {
      "utr is missing" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doUpdateSubscriptionRequest(removeFields(Seq(__ \ "utr")))

        result.status shouldBe 400
      }

      "utr contains non-numeric characters" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doUpdateSubscriptionRequest(replaceFields(Seq((__, "utr", "ABCDE12345"))))

        result.status shouldBe 400
      }

      "utr contains fewer than 10 digits" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doUpdateSubscriptionRequest(replaceFields(Seq((__, "utr", "12345"))))

        result.status shouldBe 400
      }

      "utr contains more than 10 digits" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doUpdateSubscriptionRequest(replaceFields(Seq((__, "utr", "12345678901"))))

        result.status shouldBe 400
      }

      "postcode is missing" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doUpdateSubscriptionRequest(replaceFields(Seq((__ \ "knownFacts", "postcode", ""))))

        result.status shouldBe 400
      }

      "known facts postcode is not valid" in {
        requestIsAuthenticatedWithNoEnrolments()
        val result = doUpdateSubscriptionRequest(replaceFields(Seq((__ \ "knownFacts", "postcode", "1234567"))))

        result.status shouldBe 400
      }
    }

    "throw a 500 error if " when {

      class TestSetup {
        requestIsAuthenticatedWithNoEnrolments()
        agentRecordExists(utr, true, arn)
        createAmlsSucceeds(utr, amlsDetails)
        subscriptionSucceeds(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])
        updateAmlsSucceeds(utr, Arn(arn), amlsDetails)
        allocatedPrincipalEnrolmentFails(arn)
      }

      "DES API #1170 Get Agent Record fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        agentRecordFails()

        val result = doUpdateSubscriptionRequest()

        result.status shouldBe 500
      }

      "query allocated enrolment fails in EMAC " in new TestSetup {
        val result = doUpdateSubscriptionRequest()

        result.status shouldBe 500
      }

      "delete known facts fails in EMAC " in new TestSetup {
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsFails("")

        val result = doUpdateSubscriptionRequest()

        result.status shouldBe 500
      }

      "create known facts fails in EMAC " in new TestSetup {
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds("")
        createKnownFactsFails("")

        val result = doUpdateSubscriptionRequest()

        result.status shouldBe 500
      }

      "create enrolment fails in EMAC " in new TestSetup {
        allocatedPrincipalEnrolmentNotExists(arn)
        createKnownFactsSucceeds(arn)
        enrolmentFails(groupId, arn)

        val result = doUpdateSubscriptionRequest()

        result.status shouldBe 500
      }
    }
  }

  private def doSubscriptionRequest(request: String = subscriptionRequest) =
    new Resource(s"/agent-subscription/subscription", port).postAsJson(request)
  private def doUpdateSubscriptionRequest(request: String = updateSubscriptionRequest) =
    new Resource(s"/agent-subscription/subscription", port).putAsJson(request)

  private def removeFields(fields: Seq[JsPath]): String = {
    val request = Json.parse(subscriptionRequest).as[JsObject]
    val filtered: JsObject = removeFields(request, fields)

    stringify(filtered)
  }

  private def removeFields(jsObject: JsObject, fields: Seq[JsPath]): JsObject = {
    val transformer = fields.map(field => field.json.prune).reduce((a, b) => a andThen b)
    jsObject.transform(transformer).get
  }

  private def replaceFields(fields: Seq[(JsPath, String, String)]): String = {
    val request = Json.parse(subscriptionRequest).as[JsObject]
    val filtered: JsObject = replaceFields(request, fields)

    stringify(filtered)
  }

  private def replaceFields(jsObject: JsObject, fields: Seq[(JsPath, String, String)]): JsObject = {
    val transformer = fields
      .map(field => field._1.json.update(__.read[JsObject].map(o => o ++ Json.obj(field._2 -> field._3))))
      .reduce((a, b) => a andThen b)
    jsObject.transform(transformer) match {
      case s: JsSuccess[JsObject] => s.get
      case e: JsError =>
        throw new RuntimeException(s"Unable to transform JSON: $e")
    }
  }

  private val subscriptionRequest: String =
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
       |   },
       |    "langForEmail" : "en",
       |   "amlsDetails": {
       |      "supervisoryBody":"supervisory",
       |      "membershipNumber":"12345",
       |      "membershipExpiresOn":"${LocalDate.now()}",
       |      "amlsSafeId": "amlsSafeId",
       |      "agentBPRSafeId": "agentBPRSafeId"
       |    }
       |}
     """.stripMargin

  private val subscriptionRequestWithoutTelephoneNo: String =
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
       |    "email": "agency@example.com"
       |  }
       |}
     """.stripMargin

  private val updateSubscriptionRequest =
    s"""
       |{
       |  "utr": "${utr.value}" ,
       |  "knownFacts": {
       |    "postcode": "TF3 4ER"
       |  }
       |}
    """.stripMargin
}
