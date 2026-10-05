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

import play.api.Logging
import play.api.http.Status._
import play.api.libs.json._
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.utils.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.utils.RequestSupport.hc
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

import java.time.Instant
import java.time.temporal.ChronoUnit.SECONDS
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class HipRegistrationRequest(
  requiresNameMatch: Boolean = false,
  regime: String = "ITSA",
  isAnAgent: Boolean
)

object HipRegistrationRequest {
  implicit val formats: Format[HipRegistrationRequest] = Json.format[HipRegistrationRequest]
}

@Singleton
class HipConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2,
  val metrics: Metrics
)(implicit
  val ec: ExecutionContext
)
extends HttpAPIMonitor
with Logging {

  private val baseUrl = appConfig.hipBaseUrl
  private val authToken = appConfig.hipAuthToken
  private val originatingSystem = "MDTP-ASA"
  private val transmittingSystem = "HIP"

  private def hipHeaders: Seq[(String, String)] = {
    Seq(
      "Authorization" -> s"Basic $authToken",
      "correlationid" -> UUID.randomUUID().toString,
      "X-Originating-System" -> originatingSystem,
      "X-Receipt-Date" -> Instant.now().truncatedTo(SECONDS).toString,
      "X-Transmitting-System" -> transmittingSystem
    )
  }

  def subscribeToAgentServicesOverseas(
    safeId: SafeId,
    agencyDetails: OverseasAgencyDetails,
    amlsDetails: Option[OverseasAmlsDetails]
  )(implicit
    rh: RequestHeader
  ): Future[Arn] = {
    val url = s"$baseUrl/etmp/RESTAdapter/generic/agent/subscription/${encodePathSegment(safeId.value)}"
    http
      .post(url"$url")
      .setHeader(hipHeaders: _*)
      .withBody(Json.toJson(agencyDetails)(OverseasAgencyDetails.hipWrites(amlsDetails)))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case CREATED => (response.json \ "success" \ "arn").as[Arn]
          case UNPROCESSABLE_ENTITY if (response.json \ "errors" \ "code").asOpt[String].contains("061") =>
            val arn = (response.json \ "errors" \ "text").as[String].split(" ").last
            if (Arn.isValid(arn))
              Arn(arn)
            else
              throw new RuntimeException(s"$arn is not a valid Arn")
          case status =>
            throw UpstreamErrorResponse(
              s"Failed to create overseas subscription in ETMP for safeId: $safeId, reason: ${response.body}",
              status,
              INTERNAL_SERVER_ERROR
            )
        }
      }
  }

  def subscribeToAgentServicesUk(
    safeId: SafeId,
    subscriptionRequest: SubscriptionRequest
  )(implicit
    rh: RequestHeader
  ): Future[Arn] = {
    val url = s"$baseUrl/etmp/RESTAdapter/generic/agent/subscription/${encodePathSegment(safeId.value)}"
    http
      .post(url"$url")
      .setHeader(hipHeaders: _*)
      .withBody(Json.toJson(subscriptionRequest)(SubscriptionRequest.hipWrites))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case CREATED => (response.json \ "success" \ "arn").as[Arn]
          case status =>
            throw UpstreamErrorResponse(
              s"Failed to create UK subscription in ETMP for safeId: $safeId, reason: ${response.body}",
              status,
              INTERNAL_SERVER_ERROR
            )
        }
      }
  }

  def getRegistration(
    utr: Utr
  )(implicit
    rh: RequestHeader
  ): Future[Option[HipRegistrationResponse]] = getRegistrationJson(utr).map {
    case Some(r) =>
      val innerJson = (r \ "success").as[JsObject]
      Some(
        HipRegistrationResponse(
          isAnASAgent = (innerJson \ "isAnASAgent").as[Boolean],
          organisationName = (innerJson \ "organisation" \ "organisationName").asOpt[String],
          agentReferenceNumber = (innerJson \ "agentReferenceNumber").asOpt[Arn],
          individual =
            for {
              firstName <- (innerJson \ "individual" \ "firstName").asOpt[String]
              lastName <- (innerJson \ "individual" \ "lastName").asOpt[String]
            } yield Individual(
              firstName = firstName,
              lastName = lastName
            ),
          address =
            (innerJson \ "address").validate[DesBusinessAddress] match {
              case JsSuccess(value, _) => value
              case JsError(_) => throw new Exception("HIP response has a bad address format")
            },
          emailAddress = (innerJson \ "agencyDetails" \ "agencyEmail")
            .asOpt[String]
            .orElse((innerJson \ "contactDetails" \ "emailAddress").asOpt[String]),
          primaryPhoneNumber = (innerJson \ "contactDetails" \ "primaryPhoneNumber").asOpt[String],
          safeId = (r \ "safeId").asOpt[String]
        )
      )
    case _ => None
  }

  private def getRegistrationJson(
    utr: Utr
  )(implicit rh: RequestHeader): Future[Option[JsValue]] = {
    val url = s"$baseUrl/RESTAdapter/registration/utr/${encodePathSegment(utr.value)}"
    monitor("HIP-GetAgentRegistration-POST") {
      http
        .post(url"$url")
        .setHeader(hipHeaders: _*)
        .withBody(Json.toJson(HipRegistrationRequest(isAnAgent = false)))
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case CREATED => Some(response.json)
            case UNPROCESSABLE_ENTITY if isNotFound(response.json) => None
            case error =>
              throw UpstreamErrorResponse(
                s"[HIP-GetAgentRegistration-POST] returned status: $error",
                INTERNAL_SERVER_ERROR
              )
          }
        }
        .recover { case badRequest: BadRequestException => throw new Exception(s"400 Bad Request response from HIP for utr ${utr.value}", badRequest) }
    }
  }
  private def isNotFound(r: JsValue): Boolean = (r \ "errors" \ "code").as[String].contains("002")

}
