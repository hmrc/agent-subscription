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
import javax.inject.{Inject, Named}

import com.google.inject.Singleton
import play.api.libs.json.JsValue
import play.api.libs.json.Json.parse
import uk.gov.hmrc.agentsubscription.model.{BusinessPartnerRecordFound, BusinessPartnerRecordNotFound, DesBusinessPartnerRecordApiResponse}
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

trait DesBusinessPartnerRecordApiConnector {
  def getBusinessPartnerRecord(utr: String)(implicit hc: HeaderCarrier): Future[DesBusinessPartnerRecordApiResponse]
}

@Singleton
class HttpDesBusinessPartnerRecordApiConnector @Inject()(@Named("des-baseUrl") desBaseUrl: URL,
                                                         @Named("des.authorization-token") authorizationToken: String,
                                                         @Named("des.environment") environment: String,
                                                         httpGet: HttpGet) extends DesBusinessPartnerRecordApiConnector {
  def getBusinessPartnerRecord(utr: String)(implicit hc: HeaderCarrier): Future[DesBusinessPartnerRecordApiResponse] = {
    val url: String = bprUrlFor(utr)
    val response: Future[HttpResponse] = getWithDesHeaders(url)
    response map { r =>
      r.status match {
        case 200 => {
          val businessPartnerRecord: JsValue = parse(r.body)
          BusinessPartnerRecordFound(
            (businessPartnerRecord \ "addressDetails" \ "postalCode").as[String],
            (businessPartnerRecord \ "isAnASAgent" ).as[Boolean] )
        }
        case _ => throw new RuntimeException(s"Unexpected response status from DES: ${r.status}")
      }
    } recover {
      case notFound: NotFoundException => BusinessPartnerRecordNotFound
      case invalidUtr: BadRequestException => BusinessPartnerRecordNotFound
    }
  }

  private def bprUrlFor(utr: String): String =
    new URL(desBaseUrl, s"/registration/personal-details/utr/${encodePathSegment(utr)}").toString

  private def getWithDesHeaders(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val desHeaderCarrier = hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment)
    httpGet.GET[HttpResponse](url)(implicitly[HttpReads[HttpResponse]], desHeaderCarrier)
  }
}
