/*
 * Copyright 2020 HM Revenue & Customs
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
import java.time.LocalDate

import org.mockito.ArgumentMatchers.{any, eq => eqs}
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.Eventually
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscription.RequestWithAuthority
import uk.gov.hmrc.agentsubscription.audit.{AuditService, CompaniesHouseOfficerCheck}
import uk.gov.hmrc.agentsubscription.auth.AuthActions.Provider
import uk.gov.hmrc.agentsubscription.auth.Authority
import uk.gov.hmrc.agentsubscription.connectors.CompaniesHouseApiProxyConnector
import uk.gov.hmrc.agentsubscription.model.{CompaniesHouseOfficer, Crn}
import uk.gov.hmrc.agentsubscription.support.ResettingMockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class CompaniesHouseServiceSpec extends UnitSpec with ResettingMockitoSugar with Eventually {

  private val companiesHouseConnector = resettingMock[CompaniesHouseApiProxyConnector]
  private val auditService = resettingMock[AuditService]
  private val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  val stubbedLogger = new LoggerLikeStub()
  val service: CompaniesHouseService = new CompaniesHouseService(companiesHouseConnector, auditService) {
    override def getLogger: LoggerLikeStub = stubbedLogger
  }

  private val crn = Crn("01234567")

  private val authorityUrl = new URL("http://localhost/auth/authority")
  private val hc = HeaderCarrier()
  private val provider = Provider("provId", "provType")
  private val request = RequestWithAuthority(Authority(authorityUrl, authProviderId = Some(provider.providerId), authProviderType = Some(provider.providerType), "", ""), FakeRequest())
  private val requestWithoutAuthProvider = RequestWithAuthority(Authority(authorityUrl, authProviderId = None, authProviderType = None, "", ""), FakeRequest())

  override def beforeEach(): Unit = {
    super.beforeEach()
    stubbedLogger.clear()
  }

  "knownFactCheck" should {
    "audit appropriate values when there is a successful match result" in {
      when(companiesHouseConnector.getCompanyOfficers(any[Crn])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful (List(CompaniesHouseOfficer("BROWN, David", None), CompaniesHouseOfficer("LEWIS, John", Some(LocalDate.now())))))

      val nameToMatch = "Brown"

      await(service.knownFactCheck(crn, nameToMatch)(hc, provider, ec, request))

      val expectedExtraDetail = Json.parse(
        s"""
           |{
           |  "authProviderId": "${provider.providerId}",
           |  "authProviderType": "${provider.providerType}",
           |  "crn": "${crn.value}",
           |  "nameToMatch": "$nameToMatch",
           |  "matchDetailsResponse": "match_successful"
           |}
           |""".stripMargin).asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CompaniesHouseOfficerCheck, "Check Companies House officers", expectedExtraDetail)(hc, request)
      }
    }

    "audit appropriate values when there is an unsuccessful match result" in {
      when(companiesHouseConnector.getCompanyOfficers(any[Crn])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful (List(CompaniesHouseOfficer("BROWN, David", None), CompaniesHouseOfficer("LEWIS, John", Some(LocalDate.now())))))

      val nameToMatch = "Lewis"

      await(service.knownFactCheck(crn, nameToMatch)(hc, provider, ec, request))

      val expectedExtraDetail = Json.parse(
        s"""
           |{
           |  "authProviderId": "${provider.providerId}",
           |  "authProviderType": "${provider.providerType}",
           |  "crn": "${crn.value}",
           |  "nameToMatch": "$nameToMatch",
           |  "matchDetailsResponse": "no_match"
           |}
           |""".stripMargin).asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CompaniesHouseOfficerCheck, "Check Companies House officers", expectedExtraDetail)(hc, request)
      }

      stubbedLogger.logMessages.size shouldBe 1
      stubbedLogger.logMessages.head shouldBe s"Companies House known fact check failed for $nameToMatch and crn ${crn.value}"
    }

    "audit appropriate values when no record found" in {
      when(companiesHouseConnector.getCompanyOfficers(any[Crn])(eqs(hc), any[ExecutionContext]))
        .thenReturn(
          Future successful (List.empty[CompaniesHouseOfficer]))

      val nameToMatch = "Lewis"

      await(service.knownFactCheck(crn, nameToMatch)(hc, provider, ec, request))

      val expectedExtraDetail = Json.parse(
        s"""
           |{
           |  "authProviderId": "${provider.providerId}",
           |  "authProviderType": "${provider.providerType}",
           |  "crn": "${crn.value}",
           |  "nameToMatch": "$nameToMatch",
           |  "matchDetailsResponse": "record_not_found"
           |}
           |""".stripMargin).asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CompaniesHouseOfficerCheck, "Check Companies House officers", expectedExtraDetail)(hc, request)
      }

      stubbedLogger.logMessages.size shouldBe 1
      stubbedLogger.logMessages.head shouldBe s"Companies House had no record of ${crn.value}"
    }
  }
}
