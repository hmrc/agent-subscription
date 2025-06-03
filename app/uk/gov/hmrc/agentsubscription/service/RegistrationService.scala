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

package uk.gov.hmrc.agentsubscription.service

import play.api.libs.json._
import play.api.mvc.RequestHeader
import play.api.{LoggerLike, Logging}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.audit.{AuditService, CheckAgencyStatus}
import uk.gov.hmrc.agentsubscription.auth.AuthActions.Provider
import uk.gov.hmrc.agentsubscription.connectors._
import uk.gov.hmrc.agentsubscription.model.RegistrationDetails
import uk.gov.hmrc.agentsubscription.postcodesMatch

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

private object CheckAgencyStatusAuditDetail {
  implicit val writes: OWrites[CheckAgencyStatusAuditDetail] = Json.writes[CheckAgencyStatusAuditDetail]
}

private case class CheckAgencyStatusAuditDetail(
  authProviderId: Option[String],
  authProviderType: Option[String],
  utr: Utr,
  postcode: String,
  knownFactsMatched: Boolean,
  isSubscribedToAgentServices: Option[Boolean],
  isAnAsAgentInDes: Option[Boolean],
  agentReferenceNumber: Option[Arn]
)

@Singleton
class RegistrationService @Inject() (
  desConnector: DesConnector,
  taxEnrolmentsConnector: TaxEnrolmentsConnector,
  auditService: AuditService
)(implicit ec: ExecutionContext)
    extends Logging {
  protected def getLogger: LoggerLike = logger

  def getRegistration(utr: Utr, postcode: String)(implicit
    rh: RequestHeader,
    provider: Provider
  ): Future[Option[RegistrationDetails]] =
    desConnector.getRegistration(utr) flatMap {
      case Some(
            DesRegistrationResponse(
              isAnASAgent,
              organisationName,
              None,
              agentReferenceNumber,
              businessAddress,
              email,
              primaryPhoneNumber,
              safeId
            )
          ) if businessAddress.postalCode.nonEmpty =>
        if (isAnASAgent) {
          getLogger.warn(
            s"The business partner record of type organisation associated with $utr is already subscribed with arn $agentReferenceNumber and a postcode was returned"
          )
        }

        checkRegistrationAndEnrolment(
          utr,
          postcode,
          businessAddress.postalCode,
          isAnASAgent,
          organisationName,
          agentReferenceNumber,
          businessAddress,
          email,
          primaryPhoneNumber,
          safeId
        )
      case Some(
            DesRegistrationResponse(
              isAnASAgent,
              _,
              Some(DesIndividual(first, last)),
              agentReferenceNumber,
              businessAddress,
              email,
              primaryPhoneNumber,
              safeId
            )
          ) if businessAddress.postalCode.nonEmpty =>
        if (isAnASAgent) {
          getLogger.warn(
            s"The business partner record of type individual associated with $utr is already subscribed with arn $agentReferenceNumber and a postcode was returned"
          )
        }

        checkRegistrationAndEnrolment(
          utr,
          postcode,
          businessAddress.postalCode,
          isAnASAgent,
          Some(s"$first $last"),
          agentReferenceNumber,
          businessAddress,
          email,
          primaryPhoneNumber,
          safeId
        )
      case Some(DesRegistrationResponse(isAnASAgent, _, _, agentReferenceNumber, address, _, _, _)) =>
        if (isAnASAgent) {
          getLogger.warn(
            s"The business partner record associated with $utr is already subscribed with arn $agentReferenceNumber with postcode: ${address.postalCode.nonEmpty}"
          )
          auditCheckAgencyStatus(
            utr,
            postcode,
            knownFactsMatched = false,
            Some(true),
            Some(isAnASAgent),
            agentReferenceNumber
          )
        } else {
          getLogger.warn(
            s"The business partner record associated with $utr is not subscribed with postcode: ${address.postalCode.nonEmpty}"
          )
          auditCheckAgencyStatus(utr, postcode, knownFactsMatched = false, None, Some(isAnASAgent), None)
        }
        Future.successful(None)
      case None =>
        getLogger.warn(s"No business partner record was associated with $utr")
        auditCheckAgencyStatus(utr, postcode, knownFactsMatched = false, None, None, None)
        Future.successful(None)
    }

  private def checkRegistrationAndEnrolment(
    utr: Utr,
    postcode: String,
    desPostcode: Option[String],
    isAnASAgent: Boolean,
    taxpayerName: Option[String],
    maybeArn: Option[Arn],
    businessAddress: DesBusinessAddress,
    emailAddress: Option[String],
    primaryPhoneNumber: Option[String],
    safeId: Option[String]
  )(implicit
    rh: RequestHeader,
    provider: Provider
  ): Future[Option[RegistrationDetails]] = {
    val knownFactsMatched = desPostcode.exists(postcodesMatch(_, postcode))

    if (knownFactsMatched) {
      val isSubscribedToAgentServices = maybeArn match {
        case Some(arn) if isAnASAgent => taxEnrolmentsConnector.hasPrincipalGroupIds(arn)
        case _                        => Future.successful(false)
      }

      isSubscribedToAgentServices.map { isSubscribed =>
        auditCheckAgencyStatus(
          utr,
          postcode,
          knownFactsMatched,
          isSubscribedToAgentServices = Some(isSubscribed),
          Some(isAnASAgent),
          maybeArn
        )
        Some(
          RegistrationDetails(
            isSubscribed,
            isAnASAgent,
            taxpayerName,
            businessAddress,
            emailAddress,
            primaryPhoneNumber,
            safeId
          )
        )
      }

    } else {
      auditCheckAgencyStatus(utr, postcode, knownFactsMatched, None, Some(isAnASAgent), None)
      Future.successful(None)
    }
  }

  private def auditCheckAgencyStatus(
    utr: Utr,
    postcode: String,
    knownFactsMatched: Boolean,
    isSubscribedToAgentServices: Option[Boolean],
    isAnAsAgentInDes: Option[Boolean],
    agentReferenceNumber: Option[Arn]
  )(implicit rh: RequestHeader, provider: Provider): Unit =
    auditService.auditEvent(
      CheckAgencyStatus,
      "Check agency status",
      toJsObject(
        CheckAgencyStatusAuditDetail(
          Some(provider.providerId),
          Some(provider.providerType),
          utr,
          postcode,
          knownFactsMatched,
          isSubscribedToAgentServices,
          isAnAsAgentInDes,
          agentReferenceNumber
        )
      )
    )

  private def toJsObject(detail: CheckAgencyStatusAuditDetail): JsObject = Json.toJson(detail).as[JsObject]

}
