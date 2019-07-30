/*
 * Copyright 2019 HM Revenue & Customs
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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{ Inject, Named, Singleton }
import play.api.http.Status
import play.api.libs.json._
import play.utils.UriEncoding
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

import scala.concurrent.{ ExecutionContext, Future }

case class Address(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  postalCode: String,
  countryCode: String)

case class DesSubscriptionRequest(agencyName: String, agencyAddress: Address, agencyEmail: String, telephoneNumber: Option[String])

case class DesRegistrationRequest(requiresNameMatch: Boolean = false, regime: String = "ITSA", isAnAgent: Boolean)

case class DesIndividual(firstName: String, lastName: String)

case class BusinessAddress(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  postalCode: Option[String],
  countryCode: String)

object BusinessAddress {
  implicit val format: OFormat[BusinessAddress] = Json.format
}

case class DesRegistrationResponse(
  isAnASAgent: Boolean,
  organisationName: Option[String],
  individual: Option[DesIndividual],
  agentReferenceNumber: Option[Arn],
  address: BusinessAddress,
  emailAddress: Option[String])

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

@Singleton
class DesConnector @Inject() (
  @Named("des.environment") environment: String,
  @Named("des.authorization-token") authorizationToken: String,
  @Named("des-baseUrl") baseUrl: URL,
  httpPost: HttpPost,
  httpGet: HttpGet,
  metrics: Metrics) extends Status with HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def createOverseasBusinessPartnerRecord(request: OverseasRegistrationRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SafeId] = {
    monitor("DES-Overseas-CreateRegistration-POST") {
      val url = new URL(baseUrl, "/registration/02.00.00/organisation")

      httpPost.POST[OverseasRegistrationRequest, JsValue](url.toString, request)(implicitly[Writes[OverseasRegistrationRequest]], implicitly[HttpReads[JsValue]], desHeaders, ec)
        .map(response => (response \ "safeId").as[SafeId])
        .recover {
          case e =>
            throw new RuntimeException(s"Failed to register overseas agent in ETMP for ${e.getMessage}", e)
        }
    }
  }

  def subscribeToAgentServices(safeId: SafeId, agencyDetails: OverseasAgencyDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Arn] = {
    monitor("DES-SubscribeOverseasAgent-POST") {
      httpPost.POST[OverseasAgencyDetails, JsValue](desOverseasSubscribeUrl(safeId).toString, agencyDetails)(implicitly[Writes[OverseasAgencyDetails]], implicitly[HttpReads[JsValue]], desHeaders, ec)
        .map(response => (response \ "agentRegistrationNumber").as[Arn])
        .recover {
          case e => throw new RuntimeException(s"Failed to create subscription in ETMP for safeId: $safeId ${e.getMessage}", e)
        }
    }
  }

  def subscribeToAgentServices(utr: Utr, request: DesSubscriptionRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Arn] = {
    monitor("DES-SubscribeAgent-POST") {
      httpPost.POST[DesSubscriptionRequest, JsValue](desSubscribeUrl(utr).toString, request)(implicitly[Writes[DesSubscriptionRequest]], implicitly[HttpReads[JsValue]], desHeaders, ec)
    } map {
      r => (r \ "agentRegistrationNumber").as[Arn]
    } recover {
      case e => throw new RuntimeException(s"Failed to create subscription in ETMP for $utr ${e.getMessage}", e)
    }
  }

  def getRegistration(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DesRegistrationResponse]] = {
    getRegistrationJson(utr).map {
      case Some(r) => {
        def address: BusinessAddress = (r \ "address").validate[BusinessAddress] match {
          case JsSuccess(value, _) => value
          case JsError(_) => throw InvalidBusinessAddressException
        }

        def isAnASAgent = (r \ "isAnASAgent").validate[Boolean] match {
          case JsSuccess(value, _) => true
          case JsError(_) => throw InvalidIsAnASAgentException
        }

        Some(DesRegistrationResponse(
          isAnASAgent,
          (r \ "organisation" \ "organisationName").asOpt[String],
          (r \ "individual").asOpt[DesIndividual],
          (r \ "agentReferenceNumber").asOpt[Arn],
          address,
          (r \ "agencyDetails" \ "agencyEmail").asOpt[String].orElse((r \ "contactDetails" \ "emailAddress").asOpt[String])))
      }
      case _ => None
    }
  }

  /** This method uses DES API 1170 (API 4) - Get Agent Record - UTR */
  def getAgentRecordDetails(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AgentRecord] = {
    val encodedUtr = UriEncoding.encodePathSegment(utr.value, "UTF-8")

    val url = new URL(baseUrl, s"/registration/personal-details/utr/$encodedUtr")
    getWithDesHeaders[AgentRecord]("GetAgentRecord", url)
  }

  /** This method uses DES API#1380 Get CT Reference */
  def getCorporationTaxUtr(crn: Crn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Utr] = {
    val encodedCrn = UriEncoding.encodePathSegment(crn.value, "UTF-8")

    val url = new URL(baseUrl, s"/corporation-tax/identifiers/crn/$encodedCrn")

    getWithDesHeaders[JsValue]("GetCtUtr", url).map { response =>
      (response \ "CTUTR").as[Utr]
    }
  }

  /** This method uses DES API#1385 Get VAT Known Facts Control List */
  def getVatKnownfacts(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    val encodedVrn = UriEncoding.encodePathSegment(vrn.value, "UTF-8")

    val url = new URL(baseUrl, s"/vat/known-facts/control-list/$encodedVrn")

    getWithDesHeaders[JsValue]("GetVatKnownfacts", url).map { response =>
      (response \ "dateOfReg").as[String]
    }
  }

  private def getRegistrationJson(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]] =
    monitor("DES-GetAgentRegistration-POST") {
      httpPost.POST[DesRegistrationRequest, Option[JsValue]](
        desRegistrationUrl(utr).toString,
        DesRegistrationRequest(isAnAgent = false))(
          implicitly[Writes[DesRegistrationRequest]],
          implicitly[HttpReads[Option[JsValue]]], desHeaders, ec)
    } recover {
      case badRequest: BadRequestException =>
        throw new DesConnectorException(s"400 Bad Request response from DES for utr ${utr.value}", badRequest)
    }

  private def desSubscribeUrl(utr: Utr): URL =
    new URL(baseUrl, s"/registration/agents/utr/${encodePathSegment(utr.value)}")

  private def desOverseasSubscribeUrl(safeId: SafeId): URL =
    new URL(baseUrl, s"/registration/agents/safeId/${encodePathSegment(safeId.value)}")

  private def desRegistrationUrl(utr: Utr): URL =
    new URL(baseUrl, s"/registration/individual/utr/${encodePathSegment(utr.value)}")

  private def desHeaders(implicit hc: HeaderCarrier): HeaderCarrier = {
    hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment)
  }

  private def getWithDesHeaders[A: HttpReads](apiName: String, url: URL)(
    implicit
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[A] =
    monitor(s"ConsumedAPI-DES-$apiName-GET") {
      httpGet.GET[A](url.toString)(implicitly[HttpReads[A]], desHeaders, ec)
    }

}

class DesConnectorException(val reason: String, val cause: Throwable) extends RuntimeException(reason, cause)

sealed trait DesResponseJsonException extends RuntimeException {
  def error = this match {
    case InvalidBusinessAddressException => new RuntimeException("Invalid business address found in DES response")
    case InvalidIsAnASAgentException => new RuntimeException("Invalid IsAnASAgent found in DES response")
  }
}

case object InvalidBusinessAddressException extends DesResponseJsonException
case object InvalidIsAnASAgentException extends DesResponseJsonException
