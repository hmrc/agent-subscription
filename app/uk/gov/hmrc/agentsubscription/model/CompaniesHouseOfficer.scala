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

package uk.gov.hmrc.agentsubscription.model

import play.api.libs.json._
import play.api.libs.json.Reads
import play.api.libs.functional.syntax._

case class CompaniesHouseDateOfBirth(day: Option[Int], month: Int, year: Int)

case class CompaniesHouseOfficer(name: String, dateOfBirth: Option[CompaniesHouseDateOfBirth])

object CompaniesHouseDateOfBirth {
  implicit val format: Format[CompaniesHouseDateOfBirth] = Json.format[CompaniesHouseDateOfBirth]
}

object CompaniesHouseOfficer {

  implicit val reads: Reads[CompaniesHouseOfficer] = ((__ \ "name").read[String] and
    (__ \ "date_of_birth").readNullable[CompaniesHouseDateOfBirth])(CompaniesHouseOfficer.apply _)

}

case class ReducedCompanyInformation(companyNumber: String, companyName: String, companyStatus: String)

object ReducedCompanyInformation {
  implicit val reads: Reads[ReducedCompanyInformation] = ((__ \ "company_number").read[String] and
    (__ \ "company_name").read[String] and
    (__ \ "company_status").read[String])(ReducedCompanyInformation.apply _)
}
