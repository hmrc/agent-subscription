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
import javax.inject.{Inject, Named, Singleton}

import play.api.http.Status
import play.api.libs.json.{Format, Json, Writes}
import uk.gov.hmrc.agentsubscription.model.Arn
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpPost, HttpReads, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

case class Address(addressLine1: String,
                   addressLine2: String,
                   addressLine3: Option[String] = None,
                   addressLine4: Option[String] = None,
                   postalCode: String,
                   countryCode: String)
case class DesSubscriptionRequest(safeId: String, agencyName: String, agencyAddress: Address, agencyEmail: String, telephoneNumber: String, regime: String = "ITSA")

object DesSubscriptionRequest {
  implicit val addressFormats: Format[Address] = Json.format[Address]
  implicit val formats: Format[DesSubscriptionRequest] = Json.format[DesSubscriptionRequest]
}

@Singleton
class DesConnector @Inject() (@Named("des.environment") environment: String,
                              @Named("des.authorizationToken") authorizationToken: String,
                              @Named("des.baseUrl") baseUrl: URL,
                              httpPost: HttpPost) extends Status {

  def subscribeToAgentServices(utr: String, request: DesSubscriptionRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Arn] = {
    (httpPost.POST[DesSubscriptionRequest, HttpResponse](desUrl(utr).toString, request)
        (implicitly[Writes[DesSubscriptionRequest]], implicitly[HttpReads[HttpResponse]], desHeaders)) map {
          r => Arn((r.json \ "agentReferenceNumber").as[String])
        }
  }

  private def desUrl(utr: String): URL =
    new URL(baseUrl, s"/registration/agents/utr/${encodePathSegment(utr)}")

  private def desHeaders(implicit hc: HeaderCarrier): HeaderCarrier = {
    hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment)
  }
}
