/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.{ Inject, Singleton }
import play.api.{ LoggerLike, Logging }
import play.api.libs.json.{ JsObject, Json, OWrites }
import play.api.mvc.{ AnyContent, Request }
import uk.gov.hmrc.agentsubscription.audit.{ AuditService, CompaniesHouseOfficerCheck }
import uk.gov.hmrc.agentsubscription.auth.AuthActions.Provider
import uk.gov.hmrc.agentsubscription.connectors.CompaniesHouseApiProxyConnector
import uk.gov.hmrc.agentsubscription.model.MatchDetailsResponse._
import uk.gov.hmrc.agentsubscription.model.{ Crn, MatchDetailsResponse }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

private object CheckCompaniesHouseOfficersAuditDetail {
  implicit val writes: OWrites[CheckCompaniesHouseOfficersAuditDetail] = Json.writes[CheckCompaniesHouseOfficersAuditDetail]
}

private case class CheckCompaniesHouseOfficersAuditDetail(
  authProviderId: Option[String],
  authProviderType: Option[String],
  crn: Crn,
  nameToMatch: String,
  matchDetailsResponse: MatchDetailsResponse)

@Singleton
class CompaniesHouseService @Inject() (companiesHouseConnector: CompaniesHouseApiProxyConnector, auditService: AuditService) extends Logging {

  protected def getLogger: LoggerLike = logger

  def knownFactCheck(crn: Crn, nameToMatch: String)(
    implicit
    hc: HeaderCarrier, provider: Provider, ec: ExecutionContext, request: Request[AnyContent]): Future[MatchDetailsResponse] = {
    companiesHouseConnector.getCompanyOfficers(crn, nameToMatch).map {
      case Nil =>
        getLogger.warn(s"Companies House known fact check failed for $nameToMatch and crn ${crn.value}")
        auditCompaniesHouseCheckResult(crn, nameToMatch, NoMatch)
        NoMatch
      case _ =>
        //TODO improve this by i) match the full name (using a fuzzy match) and ii) match date of birth (against CiD record)
        getLogger.info(s"successful match result for company number ${crn.value}")
        auditCompaniesHouseCheckResult(crn, nameToMatch, Match)
        Match
    }
  }

  private def auditCompaniesHouseCheckResult(crn: Crn, nameToMatch: String, matchDetailsResponse: MatchDetailsResponse)(
    implicit
    hc: HeaderCarrier, provider: Provider, request: Request[AnyContent]): Unit = {
    auditService.auditEvent(
      CompaniesHouseOfficerCheck,
      "Check Companies House officers",
      toJsObject(CheckCompaniesHouseOfficersAuditDetail(
        Some(provider.providerId),
        Some(provider.providerType),
        crn,
        nameToMatch,
        matchDetailsResponse)))
  }

  private def toJsObject(detail: CheckCompaniesHouseOfficersAuditDetail): JsObject = Json.toJson(detail).as[JsObject]

}
