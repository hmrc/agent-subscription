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

package uk.gov.hmrc.agentsubscription.controllers

import javax.inject._

import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.agentsubscription._
import uk.gov.hmrc.agentsubscription.audit.{AuditService}
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.connectors.{AuthConnector, DesConnector, DesIndividual, DesRegistrationResponse}
import uk.gov.hmrc.agentsubscription.model.{RegistrationDetails, Utr, postcodeWithoutSpacesRegex}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class RegistrationController @Inject()(val desConnector: DesConnector, override val authConnector: AuthConnector, auditService: AuditService)
  extends BaseController with AuthActions {

  def getRegistration(utr: String, postcode: String) = withAgentAffinityGroup.async { implicit request =>
    if (Utr.isValid(utr) && validPostcode(postcode))
      getRegistrationFromDes(utr, postcode)
    else
      Future successful BadRequest(Json.obj("code" -> "INVALID_UTR"))
  }

  private def getRegistrationFromDes(utr: String, postcode: String)(implicit hc: HeaderCarrier): Future[Result] = {
    desConnector.getRegistration(utr) map {
      case Some(DesRegistrationResponse(Some(desPostcode), isAnASAgent, organisationName, None)) if postcodesMatch(desPostcode, postcode) =>
        auditKnownFacts(utr, postcode, true, isAnASAgent)
        Ok(toJson(RegistrationDetails(isAnASAgent, organisationName)))
      case Some(DesRegistrationResponse(Some(desPostcode), isAnASAgent, _, Some(DesIndividual(first, last)))) if postcodesMatch(desPostcode, postcode) =>
        auditKnownFacts(utr, postcode, true, isAnASAgent) //FIXME: Does it make sense? Can an individual be an agent ?
        Ok(toJson(RegistrationDetails(isAnASAgent, Some(s"$first $last"))))
      case _ =>
        auditKnownFacts(utr, postcode, false, false)
        NotFound
    }
  }

  private def validPostcode(postcode: String): Boolean = {
    postcode.replaceAll("\\s", "").matches(postcodeWithoutSpacesRegex)
  }

  def auditKnownFacts(utr: String, postcode: String, knownFactsMatches: Boolean, isAnAgent: Boolean)(implicit hc: HeaderCarrier): Unit = {
    import play.api.libs.json._
    val authorization = hc.authorization.fold("NOT AUTHORIZED") { auth: Authorization => auth.value } //FIXME: Does it make sense?
    val path = s"/agent-subscription/registration/utr/${utr}/postcode/${postcode}" //FIXME: This looks like a hack :-(
    if (knownFactsMatches) {
      implicitly { KnownFactsSuccessAuditDetail.writes }
      auditService.auditAgencyStatusEvent(
        "Agent services registration",
        path,
        Json.toJson(
          KnownFactsSuccessAuditDetail(authorization, utr, postcode, true, isAnAgent)
        ).as[JsObject])
    } else {
      implicitly { KnownFactsFailureAuditDetail.writes }
      auditService.auditAgencyStatusEvent(
        "Agent services registration",
        path,
        Json.toJson(
          KnownFactsFailureAuditDetail(authorization, utr, postcode, false)
        ).as[JsObject])
    }
  }
}

private object KnownFactsSuccessAuditDetail { implicit val writes = Json.writes[KnownFactsSuccessAuditDetail] }
private object KnownFactsFailureAuditDetail { implicit val writes = Json.writes[KnownFactsFailureAuditDetail] }

private case class KnownFactsSuccessAuditDetail(authorization: String, utr: String, postcode: String, knownFactsMatched: Boolean, isSubscribedToAgentServices: Boolean)
private case class KnownFactsFailureAuditDetail(authorization: String, utr: String, postcode: String, knownFactsMatched: Boolean)
