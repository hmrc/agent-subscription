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

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.mvc.RequestHeader
import play.api.LoggerLike
import play.api.Logging
import uk.gov.hmrc.agentsubscription.audit.AuditService
import uk.gov.hmrc.agentsubscription.audit.CompaniesHouseOfficerCheck
import uk.gov.hmrc.agentsubscription.audit.CompaniesHouseStatusCheck
import uk.gov.hmrc.agentsubscription.auth.AuthActions.Provider
import uk.gov.hmrc.agentsubscription.connectors.CompaniesHouseApiProxyConnector
import uk.gov.hmrc.agentsubscription.model.MatchDetailsResponse._
import uk.gov.hmrc.agentsubscription.model.Crn
import uk.gov.hmrc.agentsubscription.model.MatchDetailsResponse

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

private object CheckCompaniesHouseOfficersAuditDetail {
  implicit val writes: OWrites[CheckCompaniesHouseOfficersAuditDetail] = Json.writes[CheckCompaniesHouseOfficersAuditDetail]
}

private case class CheckCompaniesHouseOfficersAuditDetail(
  authProviderId: Option[String],
  authProviderType: Option[String],
  crn: Crn,
  nameToMatch: String,
  matchDetailsResponse: MatchDetailsResponse
)

private case class CheckCompaniesHouseStatusAuditDetail(
  authProviderId: Option[String],
  authProviderType: Option[String],
  crn: Crn,
  companyStatus: Option[String],
  matchDetailsResponse: MatchDetailsResponse
)

private object CheckCompaniesHouseStatusAuditDetail {
  implicit val writes: OWrites[CheckCompaniesHouseStatusAuditDetail] = Json.writes[CheckCompaniesHouseStatusAuditDetail]
}

@Singleton
class CompaniesHouseService @Inject() (
  companiesHouseConnector: CompaniesHouseApiProxyConnector,
  auditService: AuditService
)(implicit ec: ExecutionContext)
extends Logging {

  // https://developer-specs.company-information.service.gov.uk/companies-house-public-data-api/resources/companyprofile?v=latest
  private lazy val allowedCompanyStatuses = Seq(
    "active",
    "administration",
    "voluntary-arrangement",
    "registered",
    "open"
  )

  protected def getLogger: LoggerLike = logger

  def knownFactCheck(
    crn: Crn,
    nameToMatch: String
  )(implicit
    rh: RequestHeader,
    provider: Provider
  ): Future[MatchDetailsResponse] = companiesHouseConnector.getCompanyOfficers(crn, nameToMatch).flatMap {
    case Nil =>
      getLogger.warn(s"Companies House known fact check failed for $nameToMatch and crn ${crn.value}")
      auditCompaniesHouseOfficersCheckResult(
        crn,
        nameToMatch,
        NoMatch
      )
      Future successful NoMatch
    case _ =>
      // TODO improve this by i) match the full name (using a fuzzy match) and ii) match date of birth (against CiD record)
      companyStatusCheck(crn, Some(nameToMatch))
  }

  def companyStatusCheck(
    crn: Crn,
    maybeNameToMatch: Option[String]
  )(implicit
    rh: RequestHeader,
    provider: Provider
  ): Future[MatchDetailsResponse] =
    companiesHouseConnector.getCompany(crn) map {
      case None =>
        getLogger.warn(s"Companies House API found nothing for ${crn.value}")
        auditCompaniesHouseStatusCheckResult(
          crn,
          None,
          NoMatch
        )
        NoMatch
      case Some(companyInformation) =>
        if (allowedCompanyStatuses.contains(companyInformation.companyStatus)) {
          getLogger.info(s"Found company of status ${companyInformation.companyStatus} for ${crn.value}")
          maybeNameToMatch.foreach(nameToMatch =>
            auditCompaniesHouseOfficersCheckResult(
              crn,
              nameToMatch,
              Match
            )
          )
          auditCompaniesHouseStatusCheckResult(
            crn,
            Option(companyInformation.companyStatus),
            Match
          )
          Match
        }
        else {
          getLogger.warn(s"Found company status '${companyInformation.companyStatus}' for ${crn.value}")
          auditCompaniesHouseStatusCheckResult(
            crn,
            Option(companyInformation.companyStatus),
            NotAllowed
          )
          NotAllowed
        }
    }

  private def auditCompaniesHouseOfficersCheckResult(
    crn: Crn,
    nameToMatch: String,
    matchDetailsResponse: MatchDetailsResponse
  )(implicit
    rh: RequestHeader,
    provider: Provider
  ): Unit = auditService.auditEvent(
    CompaniesHouseOfficerCheck,
    "Check Companies House officers",
    Json
      .toJson(
        CheckCompaniesHouseOfficersAuditDetail(
          Some(provider.providerId),
          Some(provider.providerType),
          crn,
          nameToMatch,
          matchDetailsResponse
        )
      )
      .as[JsObject]
  )

  private def auditCompaniesHouseStatusCheckResult(
    crn: Crn,
    companyStatus: Option[String],
    matchDetailsResponse: MatchDetailsResponse
  )(implicit
    rh: RequestHeader,
    provider: Provider
  ): Unit = auditService.auditEvent(
    CompaniesHouseStatusCheck,
    "Check Companies House company status",
    Json
      .toJson(
        CheckCompaniesHouseStatusAuditDetail(
          Some(provider.providerId),
          Some(provider.providerType),
          crn,
          companyStatus,
          matchDetailsResponse
        )
      )
      .as[JsObject]
  )

}
