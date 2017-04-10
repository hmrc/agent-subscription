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

  def currentAuthority()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Authority]] = {
    val response: Future[JsValue] = get("/auth/authority")
    response flatMap { r =>
      for {
        userDetails <- userDetails((r \ "userDetailsLink").as[String])
        enrolmentsUrl <- Future successful (r \ "enrolments").as[String]
      } yield Some(Authority(userDetails.authProviderId, userDetails.authProviderType, userDetails.affinityGroup, enrolmentsUrl))
    } recover {
      case error: Upstream4xxResponse if error.upstreamResponseCode == 401 => None
      case e => throw e
    }
  }

  private def userDetails(url: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[UserDetails] =
    get(url) map(_.as[UserDetails])

  def enrolments(url: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[Enrolment]] =
    get(url) map(_.as[List[Enrolment]])


  private def url(relativeUrl: String): URL = new URL(baseUrl, relativeUrl)

  private def get(relativeUrl: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    httpGet.GET[JsValue](url(relativeUrl).toString)(implicitly[HttpReads[JsValue]], hc)
  }
}
