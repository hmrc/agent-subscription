/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.auth

import java.net.URL

import play.api.libs.json.{ Format, Json }

case class Enrolment(key: String)

object Enrolment {
  implicit val format: Format[Enrolment] = Json.format[Enrolment]
}

object UserDetails {
  implicit val format: Format[UserDetails] = Json.format[UserDetails]
}

case class Authority(fetchedFrom: URL, authProviderId: Option[String], authProviderType: Option[String], affinityGroup: String, enrolmentsUrl: String) {
  val absoluteEnrolmentsUrl = new URL(fetchedFrom, enrolmentsUrl).toString
}

case class UserDetails(authProviderId: Option[String], authProviderType: Option[String], affinityGroup: String)
