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

package uk.gov.hmrc.agentsubscription.model

import play.api.libs.json.{ Format, Json }
import uk.gov.hmrc.agentsubscription.model.DesignatoryDetails.Person

//Add more fields as required: https://github.com/hmrc/citizen-details
case class DesignatoryDetails(person: Option[Person] = None)

object DesignatoryDetails {

  case class Person(dateOfBirth: Option[DateOfBirth] = None)

  object Person {
    implicit val format: Format[Person] = Json.format[Person]
  }

  implicit val format: Format[DesignatoryDetails] = Json.format[DesignatoryDetails]
}
