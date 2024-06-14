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

import play.api.libs.ws.WSClient
import play.api.test.Helpers.CONTENT_TYPE
import uk.gov.hmrc.agentsubscription.model.AmlsSubscriptionRecord
import uk.gov.hmrc.agentsubscription.stubs.DesStubs
import uk.gov.hmrc.agentsubscription.support.BaseISpec

import java.time.LocalDate
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, SECONDS}

class AmlsSubscriptionControllerISpec extends BaseISpec with DesStubs {

  implicit val ws = app.injector.instanceOf[WSClient]

  val duration = Duration(5, SECONDS)

  def doRequest(amlsRegNumber: String) =
    Await.result(
      ws.url(s"http://localhost:$port/agent-subscription/amls-subscription/$amlsRegNumber")
        .withHttpHeaders(CONTENT_TYPE -> "application/json")
        .get(),
      duration
    )

  "GET /amls-subscription/:amlsRegistrationNumber" should {

    "return OK with Json body when amls registration number found in ETMP" in {
      amlsSubscriptionRecordExists("XAML00000200000")

      val response = doRequest("XAML00000200000")

      response.status shouldBe 200

      response.json.as[AmlsSubscriptionRecord] shouldBe AmlsSubscriptionRecord(
        "Approved",
        "xyz",
        Some(LocalDate.parse("2021-01-01")),
        Some(LocalDate.parse("2021-12-31")),
        Some(false)
      )
    }

    "return NotFound when amls registration number is not known in ETMP" in {
      amlsSubscriptionRecordFails("XAML00000200000", 404)

      val response = doRequest("XAML00000200000")

      response.status shouldBe 404
    }

    "return BadRequest when amls registration number is invalid in ETMP" in {
      amlsSubscriptionRecordFails("XXX", 400)

      val response = doRequest("XXX")

      response.status shouldBe 400
    }

    "return Service Unavailable when DES is down" in {
      amlsSubscriptionRecordFails("XXX", 503)

      val response = doRequest("XXX")

      response.status shouldBe 500
    }

  }

}
