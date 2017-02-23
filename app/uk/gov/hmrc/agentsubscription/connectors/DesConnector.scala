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
case class DesSubscriptionRequest(agencyName: String, agencyAddress: Address, agencyEmail: String, telephoneNumber: String)

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
    new URL(baseUrl, s"/income-tax-self-assessment/agents/utr/${encodePathSegment(utr)}")

  private def desHeaders(implicit hc: HeaderCarrier): HeaderCarrier = {
    hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment)
  }
}
