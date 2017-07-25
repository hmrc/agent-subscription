/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.connectors

import java.net.URL

import org.mockito.ArgumentMatchers.{any, anyString, eq ⇒ eqs}
import org.mockito.Mockito.{verify, when}
import play.api.libs.json.{JsValue, Json}
import org.mockito.ArgumentMatchers.any
import uk.gov.hmrc.agentsubscription.auth.{Authority, Enrolment, UserDetails}
import uk.gov.hmrc.agentsubscription.connectors.AuthConnector
import uk.gov.hmrc.agentsubscription.support.ResettingMockitoSugar
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpReads}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AuthConnectorSpec extends UnitSpec with ResettingMockitoSugar {

  private implicit val hc = resettingMock[HeaderCarrier]
  private val httpGet = resettingMock[HttpGet]
  private val authConnector = new AuthConnector(new URL("http://localhost"), httpGet)
  private val authorityUrl = new URL("http://localhost/auth/authority")
  private val testAuthority = Authority(authorityUrl, authProviderId = Some("54321-credId"), authProviderType = Some("GovernmentGateway"), "", enrolmentsUrl = "relativeEnrolments")
  private val expectedEnrolmentsUrl = "http://localhost/auth/relativeEnrolments"
  private val expectedUserDetailsUrl = "http://localhost/auth/userDetailsLink"
  private val authorityJsonWithRelativeUrl = Json.obj("enrolments" -> "relativeEnrolments", "userDetailsLink" → "userDetailsLink")


  "AuthConnector.enrolments" should {
    "resolve the enrolments URL relative to the authority URL" in {

      val agentEnrolment = Enrolment("HMRC-AS-AGENT")
      when(httpGet.GET[List[Enrolment]](eqs(expectedEnrolmentsUrl))(any[HttpReads[List[Enrolment]]], any[HeaderCarrier])).thenReturn(Future successful List(agentEnrolment))
      await(authConnector.enrolments(testAuthority))
      verify(httpGet).GET[List[Enrolment]](eqs(expectedEnrolmentsUrl))(any[HttpReads[List[Enrolment]]], any[HeaderCarrier])
    }
  }
    "currentAuthority" should {
    " resolve the userDetails URL relative to the authority URL" in {

      when(httpGet.GET[JsValue](eqs(authorityUrl.toString))(any[HttpReads[JsValue]], any[HeaderCarrier]))
        .thenReturn(Future successful authorityJsonWithRelativeUrl)
      val userDetails = UserDetails(Some("1234"), Some("Agent"), "God")

      when(httpGet.GET[UserDetails](eqs(expectedUserDetailsUrl))(any[HttpReads[UserDetails]], any[HeaderCarrier])).thenReturn(Future successful userDetails)

      await(authConnector.currentAuthority())
      verify(httpGet).GET[UserDetails](eqs(expectedUserDetailsUrl))(any[HttpReads[UserDetails]], any[HeaderCarrier])
    }
  }
}
