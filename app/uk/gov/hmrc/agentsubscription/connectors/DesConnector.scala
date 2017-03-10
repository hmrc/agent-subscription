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
import play.api.libs.json._
import uk.gov.hmrc.agentsubscription.model.Arn
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier, HttpPost, HttpReads}

import scala.concurrent.{ExecutionContext, Future}

case class Address(addressLine1: String,
                   addressLine2: Option[String],
                   addressLine3: Option[String] = None,
                   addressLine4: Option[String] = None,
                   postalCode: String,
                   countryCode: String)
case class DesSubscriptionRequest(agencyName: String, agencyAddress: Address, agencyEmail: String, telephoneNumber: String, regime: String = "ITSA")

case class DesRegistrationRequest(requiresNameMatch: Boolean = false, regime: String = "ITSA", isAnAgent: Boolean)

case class DesRegistrationResponse(postalCode: Option[String], isAnASAgent: Boolean, organisationName: Option[String])


object DesSubscriptionRequest {
  implicit val addressFormats: Format[Address] = Json.format[Address]
  implicit val formats: Format[DesSubscriptionRequest] = Json.format[DesSubscriptionRequest]
}

object DesRegistrationRequest {
  implicit val formats: Format[DesRegistrationRequest] = Json.format[DesRegistrationRequest]
}

@Singleton
class DesConnector @Inject() (@Named("des.environment") environment: String,
                              @Named("des.authorization-token") authorizationToken: String,
                              @Named("des-baseUrl") baseUrl: URL,
                              httpPost: HttpPost) extends Status {

  def subscribeToAgentServices(utr: String, request: DesSubscriptionRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Arn] = {
    (httpPost.POST[DesSubscriptionRequest, JsValue](desSubscribeUrl(utr).toString, request)
        (implicitly[Writes[DesSubscriptionRequest]], implicitly[HttpReads[JsValue]], desHeaders)) map {
          r => (r \ "agentReferenceNumber").as[Arn]
        }
  }

  def getRegistration(utr: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DesRegistrationResponse]] =
    getRegistrationJson(utr) map {
      case Some(r) => Some(DesRegistrationResponse((r \ "address" \ "postalCode").asOpt[String],
                                                   (r \ "isAnASAgent").as[Boolean],
                                                   (r \ "organisation" \ "organisationName").asOpt[String]))
      case _ => None
    }

  private def getRegistrationJson(utr: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]] = {
    (httpPost.POST[DesRegistrationRequest, Option[JsValue]](desRegistrationUrl(utr).toString, DesRegistrationRequest(isAnAgent = false))
      (implicitly[Writes[DesRegistrationRequest]], implicitly[HttpReads[Option[JsValue]]], desHeaders))
  } recover {
    case badRequest: BadRequestException =>
    throw new RuntimeException(s"400 Bad Request response from DES for utr $utr", badRequest)
  }

  private def desSubscribeUrl(utr: String): URL =
    new URL(baseUrl, s"/registration/agents/utr/${encodePathSegment(utr)}")

  private def desRegistrationUrl(utr: String): URL =
    new URL(baseUrl, s"/registration/individual/utr/${encodePathSegment(utr)}")

  private def desHeaders(implicit hc: HeaderCarrier): HeaderCarrier = {
    hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment)
  }
}
