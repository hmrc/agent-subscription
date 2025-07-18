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

package uk.gov.hmrc.agentsubscription.connectors

import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.CompaniesHouseDateOfBirth
import uk.gov.hmrc.agentsubscription.model.CompaniesHouseOfficer
import uk.gov.hmrc.agentsubscription.model.Crn
import uk.gov.hmrc.agentsubscription.model.ReducedCompanyInformation
import uk.gov.hmrc.agentsubscription.stubs.CompaniesHouseStub
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.agentsubscription.support.MetricsTestSupport
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global

class CompaniesHouseApiProxyConnectorISpec
extends BaseISpec
with CompaniesHouseStub
with MetricsTestSupport
with MockitoSugar {

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val http: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
  private lazy val appConfig = app.injector.instanceOf[AppConfig]

  private lazy val connector: CompaniesHouseApiProxyConnector =
    new CompaniesHouseApiProxyConnector(
      appConfig,
      http,
      metrics
    )

  val crn = Crn("01234567")

  "GET companyOfficers" should {
    "return a list of officers who's surname matches the nameToMatch for a given CRN" in {

      givenSuccessfulCompaniesHouseResponseMultipleMatches(crn, "FERGUSON")
      val result = await(connector.getCompanyOfficers(crn, "FERGUSON"))

      result shouldBe List(
        CompaniesHouseOfficer(
          "FERGUSON, David",
          Some(CompaniesHouseDateOfBirth(
            Some(4),
            8,
            1967
          ))
        ),
        CompaniesHouseOfficer("FERGUSON, Hamish", Some(CompaniesHouseDateOfBirth(None, 4, 1974))),
        CompaniesHouseOfficer("FERGUSON, Iain Blair", Some(CompaniesHouseDateOfBirth(None, 2, 1973))),
        CompaniesHouseOfficer("FERGUSON, Mark Richard", Some(CompaniesHouseDateOfBirth(None, 10, 1972)))
      )
    }

    "return Seq.empty when Unauthorized" in {

      givenCompaniesHouseOfficersListWithStatus(
        crn.value,
        "FERGUSON",
        401
      )
      val result = await(connector.getCompanyOfficers(crn, "FERGUSON"))

      result shouldBe Seq.empty
    }

  }

  "GET company" should {
    "return reduced company information when found" in {

      givenSuccessfulGetCompanyHouseResponse(crn, "active")
      val result = await(connector.getCompany(crn))

      result shouldBe Some(ReducedCompanyInformation(
        crn.value,
        "Watford Microbreweries",
        "active"
      ))
    }

    "throw exception when Unauthorized" in {

      givenUnsuccessfulGetCompanyHouseResponse(crn, 401)

      intercept[UpstreamErrorResponse] {
        await(connector.getCompany(crn))
      }
    }

    "throw exception when Bad Request" in {

      givenUnsuccessfulGetCompanyHouseResponse(crn, 400)

      intercept[UpstreamErrorResponse] {
        await(connector.getCompany(crn))
      }
    }

  }

}
