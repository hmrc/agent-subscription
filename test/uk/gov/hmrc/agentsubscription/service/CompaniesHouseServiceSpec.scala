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

package uk.gov.hmrc.agentsubscription.service

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.concurrent.Eventually
import play.api.Logging
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscription.RequestWithAuthority
import uk.gov.hmrc.agentsubscription.audit.AuditService
import uk.gov.hmrc.agentsubscription.audit.CompaniesHouseOfficerCheck
import uk.gov.hmrc.agentsubscription.audit.CompaniesHouseStatusCheck
import uk.gov.hmrc.agentsubscription.auth.AuthActions.Provider
import uk.gov.hmrc.agentsubscription.auth.Authority
import uk.gov.hmrc.agentsubscription.connectors.CompaniesHouseApiProxyConnector
import uk.gov.hmrc.agentsubscription.model.CompaniesHouseOfficer
import uk.gov.hmrc.agentsubscription.model.Crn
import uk.gov.hmrc.agentsubscription.model.ReducedCompanyInformation
import uk.gov.hmrc.agentsubscription.support.ResettingMockitoSugar
import uk.gov.hmrc.agentsubscription.support.UnitSpec

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CompaniesHouseServiceSpec
extends UnitSpec
with ResettingMockitoSugar
with Eventually
with Logging {

  private val companiesHouseConnector = resettingMock[CompaniesHouseApiProxyConnector]
  private val auditService = resettingMock[AuditService]

  val stubbedLogger = new LoggerLikeStub()
  val service: CompaniesHouseService =
    new CompaniesHouseService(companiesHouseConnector, auditService) {
      override def getLogger: LoggerLikeStub = stubbedLogger
    }

  private val crn = Crn("01234567")

  private val authorityUrl = new URL("http://localhost/auth/authority")
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

  override def beforeEach(): Unit = {
    super.beforeEach()
    stubbedLogger.clear()
  }

  "knownFactCheck" should {
    "audit appropriate values when there is a successful match result" in {
      when(companiesHouseConnector.getCompanyOfficers(any[Crn], any[String])(any[RequestHeader]))
        .thenReturn(
          Future successful (List(
            CompaniesHouseOfficer("BROWN, David", None),
            CompaniesHouseOfficer("LEWIS, John", None)
          ))
        )

      val companyStatus = "active"

      when(companiesHouseConnector.getCompany(any[Crn])(any[RequestHeader]))
        .thenReturn(
          Future successful Some(ReducedCompanyInformation(
            "01234567",
            "Lambda Microservices",
            companyStatus
          ))
        )

      val nameToMatch = "Brown"

      await(service.knownFactCheck(crn, nameToMatch)(request, provider))

      val expectedExtraDetailCompanyOfficers = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "crn": "${crn.value}",
                  |  "nameToMatch": "$nameToMatch",
                  |  "matchDetailsResponse": "match_successful"
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      val expectedExtraDetailCompanyStatus = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "crn": "${crn.value}",
                  |  "companyStatus": "$companyStatus",
                  |  "matchDetailsResponse": "match_successful"
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(
            CompaniesHouseOfficerCheck,
            "Check Companies House officers",
            expectedExtraDetailCompanyOfficers
          )(
            request
          )
        verify(auditService)
          .auditEvent(
            CompaniesHouseStatusCheck,
            "Check Companies House company status",
            expectedExtraDetailCompanyStatus
          )(request)
      }
    }

    "audit appropriate values when there is no match" in {
      when(companiesHouseConnector.getCompanyOfficers(any[Crn], any[String])(any[RequestHeader]))
        .thenReturn(Future successful (List()))

      val nameToMatch = "Lewis"

      await(service.knownFactCheck(crn, nameToMatch)(request, provider))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "crn": "${crn.value}",
                  |  "nameToMatch": "$nameToMatch",
                  |  "matchDetailsResponse": "no_match"
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(
            CompaniesHouseOfficerCheck,
            "Check Companies House officers",
            expectedExtraDetail
          )(request)
      }

      stubbedLogger.logMessages.size shouldBe 1
      stubbedLogger.logMessages.head shouldBe s"Companies House known fact check failed for $nameToMatch and crn ${crn.value}"
    }
  }

  "companyStatusCheck" should {
    "audit appropriate values when there is a successful match result" in {

      val companyStatus = "active"

      when(companiesHouseConnector.getCompany(any[Crn])(any[RequestHeader]))
        .thenReturn(
          Future successful Some(ReducedCompanyInformation(
            "01234567",
            "Lambda Microservices",
            companyStatus
          ))
        )

      await(service.companyStatusCheck(crn, None)(request, provider))

      val expectedExtraDetailCompanyStatus = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "crn": "${crn.value}",
                  |  "companyStatus": "$companyStatus",
                  |  "matchDetailsResponse": "match_successful"
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(
            CompaniesHouseStatusCheck,
            "Check Companies House company status",
            expectedExtraDetailCompanyStatus
          )(request)
      }
    }

    "audit appropriate values when there is no match" in {

      when(companiesHouseConnector.getCompany(any[Crn])(any[RequestHeader]))
        .thenReturn(
          Future successful None
        )

      await(service.companyStatusCheck(crn, None)(request, provider))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "authProviderId": "${provider.providerId}",
                  |  "authProviderType": "${provider.providerType}",
                  |  "crn": "${crn.value}",
                  |  "matchDetailsResponse": "no_match"
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(
            CompaniesHouseStatusCheck,
            "Check Companies House company status",
            expectedExtraDetail
          )(
            request
          )
      }

      stubbedLogger.logMessages.size shouldBe 1
      stubbedLogger.logMessages.head shouldBe s"Companies House API found nothing for ${crn.value}"
    }
  }

}
