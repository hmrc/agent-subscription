/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.service

import java.net.URL
import org.mockito.ArgumentMatchers.{any, eq => eqs}
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.Eventually
import org.slf4j.{Logger, Marker}
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{LoggerLike, MarkerContext}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.RequestWithAuthority
import uk.gov.hmrc.agentsubscription.audit.{AuditService, CheckAgencyStatus}
import uk.gov.hmrc.agentsubscription.auth.AuthActions.Provider
import uk.gov.hmrc.agentsubscription.auth.Authority
import uk.gov.hmrc.agentsubscription.connectors._
import uk.gov.hmrc.agentsubscription.support.{ResettingMockitoSugar, UnitSpec}
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class RegistrationServiceSpec extends UnitSpec with ResettingMockitoSugar with Eventually {

  private val desConnector = resettingMock[DesConnector]
  private val teConnector = resettingMock[TaxEnrolmentsConnector]
  private val auditService = resettingMock[AuditService]

  val stubbedLogger = new LoggerLikeStub()
  val service: RegistrationService = new RegistrationService(desConnector, teConnector, auditService) {
    override def getLogger: LoggerLikeStub = stubbedLogger
  }

  private val authorityUrl = new URL("http://localhost/auth/authority")
  private val hc = HeaderCarrier()
  private val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  private val provider = Provider("provId", "provType")
  private val request = RequestWithAuthority(
    Authority(
      authorityUrl,
      authProviderId = Some(provider.providerId),
      authProviderType = Some(provider.providerType),
      "",
      ""
    ),
    FakeRequest()
  )
  private val requestWithoutAuthProvider =
    RequestWithAuthority(Authority(authorityUrl, authProviderId = None, authProviderType = None, "", ""), FakeRequest())

  override def beforeEach(): Unit = {
    super.beforeEach()
    stubbedLogger.clear()
  }

  "getRegistration" should {
    "audit appropriate values when a matching subscribed organisation registration is found and a matching HMRC-AS-AGENT enrolment is found" in {
      val utr = Utr("4000000009")
      val postcode = "AA1 1AA"
      val arn = Arn("TARN0000001")

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful Some(
            DesRegistrationResponse(
              isAnASAgent = true,
              Some("Organisation name"),
              None,
              Some(arn),
              BusinessAddress(
                "AddressLine1 A",
                Some("AddressLine2 A"),
                Some("AddressLine3 A"),
                Some("AddressLine4 A"),
                Some(postcode),
                "GB"
              ),
              None,
              Some("safeId")
            )
          )
        )

      when(teConnector.hasPrincipalGroupIds(eqs(arn))(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful true)

      await(service.getRegistration(utr, postcode)(hc, provider, ec, request))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "utr": "${utr.value}",
                  |  "postcode": "$postcode",
                  |  "knownFactsMatched": true,
                  |  "isSubscribedToAgentServices": true,
                  |  "isAnAsAgentInDes" : true,
                  |  "agentReferenceNumber": "TARN0000001"
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, request)
      }

      stubbedLogger.logMessages.size shouldBe 1
      stubbedLogger.logMessages.head shouldBe s"The business partner record of type organisation associated with $utr is already subscribed with arn ${Some(arn)} and a postcode was returned"
    }

    "audit appropriate values when a matching subscribed organisation registration is found and no matching HMRC-AS-AGENT enrolment is found" in {
      val utr = Utr("4000000009")
      val postcode = "AA1 1AA"
      val arn = Arn("TARN0000001")

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful Some(
            DesRegistrationResponse(
              isAnASAgent = true,
              Some("Organisation name"),
              None,
              Some(arn),
              BusinessAddress(
                "AddressLine1 A",
                Some("AddressLine2 A"),
                Some("AddressLine3 A"),
                Some("AddressLine4 A"),
                Some(postcode),
                "GB"
              ),
              None,
              Some("safeId")
            )
          )
        )

      when(teConnector.hasPrincipalGroupIds(eqs(arn))(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful false)

      await(service.getRegistration(utr, postcode)(hc, provider, ec, request))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "utr": "${utr.value}",
                  |  "postcode": "$postcode",
                  |  "knownFactsMatched": true,
                  |  "isSubscribedToAgentServices": false,
                  |  "isAnAsAgentInDes" : true,
                  |  "agentReferenceNumber": "TARN0000001"
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, request)
      }

      stubbedLogger.logMessages.size shouldBe 1
      stubbedLogger.logMessages.head shouldBe s"The business partner record of type organisation associated with $utr is already subscribed with arn ${Some(arn)} and a postcode was returned"
    }

    "audit appropriate values when a matching unsubscribed organisation registration is found" in {
      val utr = Utr("4000000009")
      val postcode = "AA1 1AA"

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful Some(
            DesRegistrationResponse(
              isAnASAgent = false,
              Some("Organisation name"),
              None,
              None,
              BusinessAddress(
                "AddressLine1 A",
                Some("AddressLine2 A"),
                Some("AddressLine3 A"),
                Some("AddressLine4 A"),
                Some(postcode),
                "GB"
              ),
              None,
              Some("safeId")
            )
          )
        )

      await(service.getRegistration(utr, postcode)(hc, provider, ec, request))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "utr": "${utr.value}",
                  |  "postcode": "$postcode",
                  |  "knownFactsMatched": true,
                  |  "isSubscribedToAgentServices": false,
                  |  "isAnAsAgentInDes" : false
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, request)
      }

      stubbedLogger.logMessages.size shouldBe 0
    }

    "audit appropriate values when the organisation record associated with the utr does not have a matching postcode" in {
      val utr = Utr("4000000009")
      val suppliedPostcode = "AA1 1AA"

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful Some(
            DesRegistrationResponse(
              isAnASAgent = false,
              Some("Organisation name"),
              None,
              None,
              BusinessAddress(
                "AddressLine1 A",
                Some("AddressLine2 A"),
                Some("AddressLine3 A"),
                Some("AddressLine4 A"),
                Some("XX9 9XX"),
                "GB"
              ),
              None,
              Some("safeId")
            )
          )
        )

      await(service.getRegistration(utr, suppliedPostcode)(hc, provider, ec, request))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "utr": "${utr.value}",
                  |  "postcode": "$suppliedPostcode",
                  |  "knownFactsMatched": false,
                  |  "isAnAsAgentInDes" : false
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, request)
      }

      stubbedLogger.logMessages.size shouldBe 0
    }

    "audit appropriate values when a matching subscribed individual registration is found and a matching HMRC-AS-AGENT enrolment is found" in {
      val utr = Utr("4000000009")
      val postcode = "AA1 1AA"
      val arn = Some(Arn("AARN0000002"))

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful Some(
            DesRegistrationResponse(
              isAnASAgent = true,
              None,
              Some(DesIndividual("First", "Last")),
              arn,
              BusinessAddress(
                "AddressLine1 A",
                Some("AddressLine2 A"),
                Some("AddressLine3 A"),
                Some("AddressLine4 A"),
                Some(postcode),
                "GB"
              ),
              None,
              Some("safeId")
            )
          )
        )

      when(teConnector.hasPrincipalGroupIds(eqs(arn.get))(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful true)

      await(service.getRegistration(utr, postcode)(hc, provider, ec, request))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "utr": "${utr.value}",
                  |  "postcode": "$postcode",
                  |  "knownFactsMatched": true,
                  |  "isSubscribedToAgentServices": true,
                  |  "isAnAsAgentInDes" : true,
                  |  "agentReferenceNumber": "AARN0000002"
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, request)
      }

      stubbedLogger.logMessages.size shouldBe 1
      stubbedLogger.logMessages.head shouldBe s"The business partner record of type individual associated with $utr is already subscribed with arn $arn and a postcode was returned"
    }

    "audit appropriate values when a matching subscribed individual registration is found and no matching HMRC-AS-AGENT enrolment is found" in {
      val utr = Utr("4000000009")
      val postcode = "AA1 1AA"
      val arn = Some(Arn("AARN0000002"))

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful Some(
            DesRegistrationResponse(
              isAnASAgent = true,
              None,
              Some(DesIndividual("First", "Last")),
              arn,
              BusinessAddress(
                "AddressLine1 A",
                Some("AddressLine2 A"),
                Some("AddressLine3 A"),
                Some("AddressLine4 A"),
                Some(postcode),
                "GB"
              ),
              None,
              Some("safeId")
            )
          )
        )

      when(teConnector.hasPrincipalGroupIds(eqs(arn.get))(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful false)

      await(service.getRegistration(utr, postcode)(hc, provider, ec, request))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "utr": "${utr.value}",
                  |  "postcode": "$postcode",
                  |  "knownFactsMatched": true,
                  |  "isSubscribedToAgentServices": false,
                  |  "isAnAsAgentInDes" : true,
                  |  "agentReferenceNumber": "AARN0000002"
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, request)
      }

      stubbedLogger.logMessages.size shouldBe 1
      stubbedLogger.logMessages.head shouldBe s"The business partner record of type individual associated with $utr is already subscribed with arn $arn and a postcode was returned"
    }

    "audit appropriate values when a matching unsubscribed individual registration is found" in {
      val utr = Utr("4000000009")
      val postcode = "AA1 1AA"

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful Some(
            DesRegistrationResponse(
              isAnASAgent = false,
              None,
              Some(DesIndividual("First", "Last")),
              None,
              BusinessAddress(
                "AddressLine1 A",
                Some("AddressLine2 A"),
                Some("AddressLine3 A"),
                Some("AddressLine4 A"),
                Some(postcode),
                "GB"
              ),
              None,
              Some("safeId")
            )
          )
        )

      await(service.getRegistration(utr, postcode)(hc, provider, ec, request))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "utr": "${utr.value}",
                  |  "postcode": "$postcode",
                  |  "knownFactsMatched": true,
                  |  "isSubscribedToAgentServices": false,
                  |  "isAnAsAgentInDes": false
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, request)
      }

      stubbedLogger.logMessages.size shouldBe 0
    }

    "audit appropriate values when the individual record associated with the utr does not have a matching postcode" in {
      val utr = Utr("4000000009")
      val suppliedPostcode = "AA1 1AA"

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful Some(
            DesRegistrationResponse(
              isAnASAgent = false,
              None,
              Some(DesIndividual("First", "Last")),
              None,
              BusinessAddress(
                "AddressLine1 A",
                Some("AddressLine2 A"),
                Some("AddressLine3 A"),
                Some("AddressLine4 A"),
                Some("XX9 9XX"),
                "GB"
              ),
              None,
              Some("safeId")
            )
          )
        )

      await(service.getRegistration(utr, suppliedPostcode)(hc, provider, ec, request))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "utr": "${utr.value}",
                  |  "postcode": "$suppliedPostcode",
                  |  "knownFactsMatched": false,
                  |  "isAnAsAgentInDes": false
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, request)
      }

      stubbedLogger.logMessages.size shouldBe 0
    }

    "tolerate optional fields being absent (agentReferenceNumber, postcode) where not subscribed" in {
      val utr = Utr("4000000009")
      val suppliedPostcode = "AA1 1AA"

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful Some(
            DesRegistrationResponse(
              isAnASAgent = false,
              None,
              None,
              None,
              BusinessAddress(
                "AddressLine1 A",
                Some("AddressLine2 A"),
                Some("AddressLine3 A"),
                Some("AddressLine4 A"),
                None,
                "GB"
              ),
              None,
              Some("safeId")
            )
          )
        )

      await(service.getRegistration(utr, suppliedPostcode)(hc, provider, ec, requestWithoutAuthProvider))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "utr": "${utr.value}",
                  |  "postcode": "$suppliedPostcode",
                  |  "knownFactsMatched": false,
                  |  "isAnAsAgentInDes": false
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, requestWithoutAuthProvider)
      }

      stubbedLogger.logMessages.size shouldBe 1
      stubbedLogger.logMessages.head shouldBe s"The business partner record associated with $utr is not subscribed with postcode: false"
    }

    "tolerate optional fields being absent (agentReferenceNumber, postcode) where subscribed" in {
      val utr = Utr("4000000009")
      val suppliedPostcode = "AA1 1AA"

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful Some(
            DesRegistrationResponse(
              isAnASAgent = true,
              None,
              None,
              None,
              BusinessAddress(
                "AddressLine1 A",
                Some("AddressLine2 A"),
                Some("AddressLine3 A"),
                Some("AddressLine4 A"),
                None,
                "GB"
              ),
              None,
              Some("safeId")
            )
          )
        )

      await(service.getRegistration(utr, suppliedPostcode)(hc, provider, ec, requestWithoutAuthProvider))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "utr": "${utr.value}",
                  |  "postcode": "$suppliedPostcode",
                  |  "knownFactsMatched": false,
                  |  "isSubscribedToAgentServices": true,
                  |  "isAnAsAgentInDes": true
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, requestWithoutAuthProvider)
      }

      stubbedLogger.logMessages.size shouldBe 1
      stubbedLogger.logMessages.head shouldBe s"The business partner record associated with $utr is already subscribed with arn None with postcode: false"
    }

    "audit appropriate values when no record is found" in {
      val utr = Utr("4000000009")
      val postcode = "AA1 1AA"

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext])).thenReturn(Future successful None)
      await(service.getRegistration(utr, postcode)(hc, provider, ec, request))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "utr": "${utr.value}",
                  |  "postcode": "$postcode",
                  |  "knownFactsMatched": false
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, request)
      }

      stubbedLogger.logMessages.size shouldBe 1
      stubbedLogger.logMessages.head shouldBe s"No business partner record was associated with $utr"
    }
  }

}

class LoggerLikeStub extends LoggerLike {

  val logMessages: mutable.Buffer[String] = mutable.Buffer()

  override val logger: Logger = null

  implicit val markerContext: MarkerContext = new MarkerContext {
    override def marker: Option[Marker] = None
  }

  override def warn(msg: => String)(implicit mc: MarkerContext): Unit = {
    logMessages += msg
    ()
  }

  override def info(msg: => String)(implicit mc: MarkerContext): Unit = {
    logMessages += msg
    ()
  }

  def clear(): Unit = {
    logMessages.clear()
    ()
  }
}
