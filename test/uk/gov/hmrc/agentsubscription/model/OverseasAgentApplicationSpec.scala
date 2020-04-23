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

package uk.gov.hmrc.agentsubscription.model

import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec

class OverseasAgentApplicationSpec extends UnitSpec {

  val ukAddressJson =
    """
      |{
      |    "addressLine1": "Mandatory Address Line 1",
      |    "addressLine2": "Mandatory Address Line 2",
      |    "postalCode": "SW4 7QH",
      |    "countryCode": "GB"
      |  }
      |""".stripMargin

  val overseasAddressJson =
    """
      |{
      |    "addressLine1": "Mandatory Address Line 1",
      |    "addressLine2": "Mandatory Address Line 2",
      |    "addressLine4": "SW4 7QH",
      |    "countryCode": "AT"
      |}
      |""".stripMargin

  "UkOverseasAddress" should {
    "parse json correctly for uk address" in {

      val address = Json.parse(ukAddressJson).as[UkAddressForOverseas]

      address shouldBe UkAddressForOverseas(
        addressLine1 = "Mandatory Address Line 1",
        addressLine2 = "Mandatory Address Line 2",
        addressLine3 = None,
        addressLine4 = None,
        postalCode = "SW4 7QH",
        countryCode = "GB")
    }

    "parse json correctly for overseas address" in {

      val address = Json.parse(overseasAddressJson).as[OverseasBusinessAddress]

      address shouldBe OverseasBusinessAddress(
        addressLine1 = "Mandatory Address Line 1",
        addressLine2 = "Mandatory Address Line 2",
        addressLine3 = None,
        addressLine4 = Some("SW4 7QH"),
        countryCode = "AT")
    }
  }
}

