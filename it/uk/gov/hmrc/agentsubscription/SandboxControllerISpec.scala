/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription

import org.scalatestplus.play.OneServerPerSuite
import uk.gov.hmrc.agentsubscription.support.Resource
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

import scala.language.postfixOps

class SandboxControllerISpec extends UnitSpec with OneServerPerSuite {
  "GET of /agencies/subscriptions/:subscriptionId" should {
    "return a subscription request" in {
      val response = await(getSubscription)

      response.status shouldBe 200
      val json = response.json

      (json \ "arn").as[String] shouldBe "00010002-0003-0004-0005-000600070008"
      (json \ "name").as[String] shouldBe "DDCW Agency Ltd"
      (json \ "address" \ "lines").head.as[String] shouldBe "1 Main St"
      (json \ "address" \ "town").as[String] shouldBe "Sometown"
      (json \ "address" \ "postcode").as[String] shouldBe "A11 1AA"
      (json \ "telephone").as[String] shouldBe "0123 456 7890"
      (json \ "email").as[String] shouldBe "me@example.com"
      (json \ "service").as[String] shouldBe "mtd-sa"
      (json \ "utr").as[String] shouldBe "0123456789"
      (json \ "agentCode").as[String] shouldBe "1234567890"
    }
  }

  "POST to /agencies/subscriptions" should {
    "include a Location header in the response" in {
      val response: HttpResponse = await(createASubscription)

      response.status shouldBe 202
      response.header("location").get should startWith("/agent-subscription/sandbox/agencies/subscriptions/")
    }
  }

  private def createASubscription = {
    new Resource("/sandbox/agencies/subscriptions", port).postEmpty
  }

  private def getSubscription = {
    new Resource("/sandbox/agencies/subscriptions/00010002-0003-0004-0005-000600070008", port).get
  }
}
