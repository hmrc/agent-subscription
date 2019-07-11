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

package uk.gov.hmrc.agentsubscription.model.subscriptionJourney

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json._

case class RegisteredDetails(membershipNumber: String, membershipExpiresOn: LocalDate)

object RegisteredDetails {
  implicit val format = Json.format[RegisteredDetails]
}

case class PendingDetails(appliedOn: LocalDate)

object PendingDetails {
  implicit val format = Json.format[PendingDetails]
}

case class AMLSDetails(supervisoryBody: String, details: Either[PendingDetails, RegisteredDetails])

object AMLSDetails {

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  implicit val format = new Format[AMLSDetails] {
    override def reads(json: JsValue): JsResult[AMLSDetails] = {
      val supervisoryBody = (json \ "supervisoryBody").as[String]

      val mayBeMembershipNumber = (json \ "membershipNumber").asOpt[String]

      mayBeMembershipNumber match {

        case Some(membershipNumber) =>
          val membershipExpiresOn = LocalDate.parse((json \ "membershipExpiresOn").as[String], formatter)
          JsSuccess(AMLSDetails(supervisoryBody, Right(RegisteredDetails(membershipNumber, membershipExpiresOn))))

        case None =>
          val appliedOn = LocalDate.parse((json \ "appliedOn").as[String], formatter)
          JsSuccess(AMLSDetails(supervisoryBody, Left(PendingDetails(appliedOn))))
      }
    }

    override def writes(amlsDetails: AMLSDetails): JsValue = {

      val detailsJson = amlsDetails.details match {
        case Right(registeredDetails) => Json.toJson(registeredDetails)
        case Left(pendingDetails) => Json.toJson(pendingDetails)
      }

      Json.obj("supervisoryBody" -> amlsDetails.supervisoryBody).deepMerge(detailsJson.as[JsObject])
    }
  }
}

