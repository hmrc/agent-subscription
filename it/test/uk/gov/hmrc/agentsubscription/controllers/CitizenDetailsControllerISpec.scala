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

package uk.gov.hmrc.agentsubscription.controllers

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import play.api.libs.ws.WSClient
import play.api.test.Helpers.AUTHORIZATION
import play.api.test.Helpers.CONTENT_TYPE
import uk.gov.hmrc.agentsubscription.model.DateOfBirth
import uk.gov.hmrc.agentsubscription.model.DesignatoryDetails
import uk.gov.hmrc.agentsubscription.model.DesignatoryDetails.Person
import uk.gov.hmrc.agentsubscription.stubs.AuthStub
import uk.gov.hmrc.agentsubscription.stubs.CitizenDetailsStubs
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.domain.Nino

import scala.concurrent.Await
import scala.concurrent.duration._

class CitizenDetailsControllerISpec
extends BaseISpec
with CitizenDetailsStubs
with AuthStub {

  implicit val ws: WSClient = app.injector.instanceOf[WSClient]

  val duration = Duration(5, SECONDS)

  val nino = Nino("XX121212B")
  val dobString = "1900-01-01"
  val dtf = DateTimeFormatter.ofPattern("yyyy-MM-DD")
  val dob = DateOfBirth(LocalDate.parse(dobString, dtf))

  def doRequest(nino: Nino) = Await.result(
    ws.url(s"http://localhost:$port/agent-subscription/citizen-details/${nino.value}/designatory-details")
      .withHttpHeaders(CONTENT_TYPE -> "application/json")
      .withHttpHeaders(AUTHORIZATION -> "Bearer XYZ")
      .get(),
    duration
  )

  "GET /citizen-details/${nino}/designatory-details" should {
    "return 200 when nino is found in Citizen details and the dob returned matches" in {
      requestIsAuthenticatedWithNoEnrolments()
      givencitizenDetailsFoundForNino(
        nino.value,
        dobString,
        Some("Matchmaker")
      )

      val response = doRequest(nino)
      response.status shouldBe 200

      response.json.as[DesignatoryDetails] shouldBe DesignatoryDetails(
        Some(Person(
          Some("Matchmaker"),
          Some(dob),
          deceased = Some(false)
        ))
      )
    }

    "return 404 when DesignatoryDetails are not found for a passed in nino" in {
      requestIsAuthenticatedWithNoEnrolments()
      givenCitizenDetailsNotFoundForNino(nino.value)

      val response = doRequest(nino)
      response.status shouldBe 404
    }

  }

}
