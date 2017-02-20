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
import uk.gov.hmrc.play.http._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

@Singleton
class AuthConnector @Inject() (@Named("auth-baseUrl") baseUrl: URL, httpGet: HttpGet) {
  def isAuthenticated()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val response: Future[HttpResponse] = get("/auth/authority")
    response map { r =>
      r.status match {
        case 200 =>  true
        case _ => throw new RuntimeException(s"Unexpected response status from Auth: ${r.status}")
      }
    } recover {
      case error: Upstream4xxResponse if (error.upstreamResponseCode == 401) => false
    }
  }

  private def url(relativeUrl: String): URL = new URL(baseUrl, relativeUrl)

  private def get(relativeUrl: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    httpGet.GET[HttpResponse](url(relativeUrl).toString)(implicitly[HttpReads[HttpResponse]], hc)
  }
}
