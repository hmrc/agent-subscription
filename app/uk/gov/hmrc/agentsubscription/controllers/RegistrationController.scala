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

import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.agentsubscription.connectors.DesBusinessPartnerRecordApiConnector
import uk.gov.hmrc.agentsubscription.model.{BusinessPartnerRecordFound, DesBusinessPartnerRecordApiResponse, RegistrationDetails}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

@Singleton
class RegistrationController @Inject()(val desConnector: DesBusinessPartnerRecordApiConnector) extends BaseController {
  def getRegistration(utr: String, postcode:String) = Action.async { implicit request =>
    desConnector.getBusinessPartnerRecord(utr)  map { desResponse: DesBusinessPartnerRecordApiResponse =>
      desResponse match {
        case businessPartnerRecord: BusinessPartnerRecordFound => businessPartnerRecord.postalCode == postcode match {
          case true => Ok( toJson( RegistrationDetails( businessPartnerRecord.isSubscribedToAgentServices ) ) )
          case false => NotFound
        }
        case _ => NotFound
      }
    }
  }
}
