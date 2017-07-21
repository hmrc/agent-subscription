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

package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL
import javax.inject._

import com.google.inject.Singleton
import play.api.libs.json.JsValue
import uk.gov.hmrc.agentsubscription.auth.{Authority, Enrolment, UserDetails}
import uk.gov.hmrc.play.http._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

@Singleton
class AuthConnector @Inject() (@Named("auth-baseUrl") baseUrl: URL, httpGet: HttpGet) {
  val authorityUrl = new URL(baseUrl, "/auth/authority")
  def currentAuthority()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Authority]] = {
    val response: Future[JsValue] = httpGet.GET[JsValue](authorityUrl.toString)
    response flatMap { r =>
      for {
        userDetails <- userDetails(authorityUrl, (r \ "userDetailsLink").as[String])
        enrolmentsUrl <- Future successful (r \ "enrolments").as[String]
      } yield Some(Authority(authorityUrl, userDetails.authProviderId, userDetails.authProviderType, userDetails.affinityGroup, enrolmentsUrl))
    } recover {
      case error: Upstream4xxResponse if error.upstreamResponseCode == 401 => None
      case e => throw e
    }
  }

  private def userDetails(authorityUrl: URL, userDetailsLink: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[UserDetails] = {
    val absoluteUserDetailsUrl = new URL(authorityUrl, userDetailsLink).toString
    httpGet.GET[UserDetails](absoluteUserDetailsUrl)
  }

  def enrolments(authority: Authority)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[Enrolment]] = {
    httpGet.GET[List[Enrolment]](authority.absoluteEnrolmentsUrl)
  }
}
