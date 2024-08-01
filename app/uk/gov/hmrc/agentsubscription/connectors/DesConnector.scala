/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.utils.UriEncoding
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HttpErrorFunctions._
import play.api.http.Status._
import uk.gov.hmrc.agentsubscription.utils.HttpAPIMonitor
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.net.URL
import java.util.UUID

case class Address(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  postalCode: String,
  countryCode: String
)

case class DesSubscriptionRequest(
  agencyName: String,
  agencyAddress: Address,
  agencyEmail: String,
  telephoneNumber: Option[String]
)

case class DesRegistrationRequest(requiresNameMatch: Boolean = false, regime: String = "ITSA", isAnAgent: Boolean)

case class DesIndividual(firstName: String, lastName: String)

case class BusinessAddress(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  postalCode: Option[String],
  countryCode: String
)

object BusinessAddress {
  implicit val format: OFormat[BusinessAddress] = Json.format
}

case class DesRegistrationResponse(
  isAnASAgent: Boolean,
  organisationName: Option[String],
  individual: Option[DesIndividual],
  agentReferenceNumber: Option[Arn],
  address: BusinessAddress,
  emailAddress: Option[String],
  primaryPhoneNumber: Option[String],
  safeId: Option[String]
)

object DesIndividual {
  implicit val formats: Format[DesIndividual] = Json.format[DesIndividual]
}

object DesSubscriptionRequest {
  implicit val addressFormats: Format[Address] = Json.format[Address]
  implicit val formats: Format[DesSubscriptionRequest] = Json.format[DesSubscriptionRequest]
}

object DesRegistrationRequest {
  implicit val formats: Format[DesRegistrationRequest] = Json.format[DesRegistrationRequest]
}

case class HeadersConfig(hc: HeaderCarrier, explicitHeaders: Seq[(String, String)])

@Singleton
class DesConnector @Inject() (appConfig: AppConfig, http: HttpClient, val metrics: Metrics)(implicit
  val ec: ExecutionContext
) extends HttpAPIMonitor {

  val baseUrl: String = appConfig.desBaseUrl
  val environment: String = appConfig.desEnvironment
  val authToken: String = appConfig.desAuthToken

  private val Environment = "Environment"
  private val CorrelationId = "CorrelationId"

  def createOverseasBusinessPartnerRecord(
    request: OverseasRegistrationRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SafeId] = {
    val url = s"$baseUrl/registration/02.00.00/organisation"
    val headersConfig = makeHeadersConfig(url)
    monitor("ConsumedAPI-DES-Overseas-CreateRegistration-POST") {
      http
        .POST[OverseasRegistrationRequest, HttpResponse](url, request, headers = headersConfig.explicitHeaders)(
          implicitly,
          implicitly,
          headersConfig.hc,
          ec
        )
        .map { response =>
          response.status match {
            case s if is2xx(s) => (response.json \ "safeId").as[SafeId]
            case s             => throw new RuntimeException(s"Failed to register overseas agent in ETMP for $s")
          }
        }
    }
  }

  def subscribeToAgentServices(safeId: SafeId, agencyDetails: OverseasAgencyDetails)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Arn] = {
    val url = desOverseasSubscribeUrl(safeId)
    val headersConfig = makeHeadersConfig(url)
    monitor("ConsumedAPI-DES-SubscribeOverseasAgent-POST") {
      http
        .POST[OverseasAgencyDetails, HttpResponse](
          url,
          agencyDetails,
          headers = headersConfig.explicitHeaders
        )(implicitly, implicitly, headersConfig.hc, ec)
        .map { response =>
          response.status match {
            case s if is2xx(s) => (response.json \ "agentRegistrationNumber").as[Arn]
            case s =>
              throw new RuntimeException(
                s"Failed to create subscription in ETMP for safeId: $safeId status $s",
                UpstreamErrorResponse(s"Upstream Error at: $url", s)
              )
          }
        }
    }
  }

  def subscribeToAgentServices(utr: Utr, request: DesSubscriptionRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Arn] = {
    val url = desSubscribeUrl(utr)
    val headersConfig = makeHeadersConfig(url)
    monitor("ConsumedAPI-DES-SubscribeAgent-POST") {
      http
        .POST[DesSubscriptionRequest, HttpResponse](url, request, headers = headersConfig.explicitHeaders)(
          implicitly,
          implicitly,
          headersConfig.hc,
          ec
        )
        .map { response =>
          response.status match {
            case s if is2xx(s) => (response.json \ "agentRegistrationNumber").as[Arn]
            case s if s == CONFLICT =>
              throw new RuntimeException(
                s"Failed to create subscription in ETMP for $utr status: $s",
                UpstreamErrorResponse(s"Unexpected response: $s from: $url", s)
              )
            case s =>
              throw new RuntimeException(s"Failed to create subscription in ETMP for $utr status: $s")
          }
        }
    }
  }

  def getRegistration(
    utr: Utr
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DesRegistrationResponse]] =
    getRegistrationJson(utr).map {
      case Some(r) =>
        def address: BusinessAddress = (r \ "address").validate[BusinessAddress] match {
          case JsSuccess(value, _) => value
          case JsError(_)          => throw InvalidBusinessAddressException
        }

        def isAnASAgent = (r \ "isAnASAgent").validate[Boolean] match {
          case JsSuccess(value, _) => value
          case JsError(_)          => throw InvalidIsAnASAgentException
        }

        Some(
          DesRegistrationResponse(
            isAnASAgent,
            (r \ "organisation" \ "organisationName").asOpt[String],
            (r \ "individual").asOpt[DesIndividual],
            (r \ "agentReferenceNumber").asOpt[Arn],
            address,
            (r \ "agencyDetails" \ "agencyEmail")
              .asOpt[String]
              .orElse((r \ "contactDetails" \ "emailAddress").asOpt[String]),
            (r \ "contactDetails" \ "primaryPhoneNumber").asOpt[String],
            (r \ "safeId").asOpt[String]
          )
        )
      case _ => None
    }

  /** This method uses DES API 1170 (API 4) - Get Agent Record - UTR */
  def getAgentRecordDetails(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AgentRecord] = {
    val encodedUtr = UriEncoding.encodePathSegment(utr.value, "UTF-8")

    val url = s"$baseUrl/registration/personal-details/utr/$encodedUtr"
    getWithDesHeaders("GetAgentRecord", url).map(_.as[AgentRecord])
  }

  /** This method uses DES API#1380 Get CT Reference */
  def getCorporationTaxUtr(crn: Crn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Utr] = {
    val encodedCrn = UriEncoding.encodePathSegment(crn.value, "UTF-8")

    val url = s"$baseUrl/corporation-tax/identifiers/crn/$encodedCrn"

    getWithDesHeaders("GetCtUtr", url).map { response =>
      (response \ "CTUTR").as[Utr]
    }
  }

  /** This method uses DES API#1385 Get VAT Known Facts Control List */
  def getVatKnownfacts(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    val encodedVrn = UriEncoding.encodePathSegment(vrn.value, "UTF-8")

    val url = s"$baseUrl/vat/known-facts/control-list/$encodedVrn"

    getWithDesHeaders("GetVatKnownfacts", url).map { response =>
      (response \ "dateOfReg").as[String]
    }
  }

  // API #1028 Get Subscription Status
  def getAmlsSubscriptionStatus(
    amlsRegistrationNumber: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AmlsSubscriptionRecord] = {

    val url = s"$baseUrl/anti-money-laundering/subscription/$amlsRegistrationNumber/status"

    getWithDesHeaders("GetAmlsSubscriptionStatus", url).map { response =>
      response.as[AmlsSubscriptionRecord]
    }
  }

  private def getRegistrationJson(
    utr: Utr
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]] = {
    val url = desRegistrationUrl(utr)
    val headersConfig = makeHeadersConfig(url)
    monitor("DES-GetAgentRegistration-POST") {
      http
        .POST[DesRegistrationRequest, HttpResponse](
          url,
          DesRegistrationRequest(isAnAgent = false),
          headers = headersConfig.explicitHeaders
        )
        .map { response =>
          response.status match {
            case OK        => Option(response.json)
            case NOT_FOUND => None
            case error =>
              throw UpstreamErrorResponse(
                s"[DES-GetAgentRegistration-POST] returned status: $error",
                INTERNAL_SERVER_ERROR
              )
          }
        }
    }
      .recover { case badRequest: BadRequestException =>
        throw new DesConnectorException(s"400 Bad Request response from DES for utr ${utr.value}", badRequest)
      }
  }

  private def desSubscribeUrl(utr: Utr): String =
    s"$baseUrl/registration/agents/utr/${encodePathSegment(utr.value)}"

  private def desOverseasSubscribeUrl(safeId: SafeId): String =
    s"$baseUrl/registration/agents/safeId/${encodePathSegment(safeId.value)}"

  private def desRegistrationUrl(utr: Utr): String =
    s"$baseUrl/registration/individual/utr/${encodePathSegment(utr.value)}"

  def makeHeadersConfig(url: String)(implicit hc: HeaderCarrier): HeadersConfig = {

    val isInternalHost = appConfig.internalHostPatterns.exists(_.pattern.matcher(new URL(url).getHost).matches())

    val baseHeaders = Seq(
      Environment   -> s"$environment",
      CorrelationId -> UUID.randomUUID().toString
    )
    val additionalHeaders =
      if (isInternalHost) Seq.empty
      else
        Seq(
          HeaderNames.authorisation -> s"Bearer $authToken",
          HeaderNames.xRequestId    -> hc.requestId.map(_.value).getOrElse(UUID.randomUUID().toString)
        ) ++ hc.sessionId.fold(Seq.empty[(String, String)])(x => Seq(HeaderNames.xSessionId -> x.value))

    HeadersConfig(
      if (isInternalHost) hc.copy(authorization = Some(Authorization(s"Bearer $authToken")))
      else hc,
      baseHeaders ++ additionalHeaders
    )
  }

  private def getWithDesHeaders(apiName: String, url: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[JsValue] = {
    val headersConfig = makeHeadersConfig(url)

    monitor(s"ConsumedAPI-DES-$apiName-GET") {
      http
        .GET[HttpResponse](url, headers = headersConfig.explicitHeaders)(implicitly, headersConfig.hc, ec)
        .map { response =>
          response.status match {
            case s if is2xx(s) => response.json
            case NOT_FOUND     => throw new NotFoundException(s"Received Not Found at:$apiName")
            case BAD_REQUEST   => throw new BadRequestException(s"Bad Request at: $apiName")
            case s             => throw UpstreamErrorResponse(s"$apiName", s)
            //
          }
        }
    }
  }
}

class DesConnectorException(val reason: String, val cause: Throwable) extends RuntimeException(reason, cause)

sealed trait DesResponseJsonException extends RuntimeException {
  def error = this match {
    case InvalidBusinessAddressException => new RuntimeException("Invalid business address found in DES response")
    case InvalidIsAnASAgentException     => new RuntimeException("Invalid IsAnASAgent found in DES response")
  }
}

case object InvalidBusinessAddressException extends DesResponseJsonException

case object InvalidIsAnASAgentException extends DesResponseJsonException
