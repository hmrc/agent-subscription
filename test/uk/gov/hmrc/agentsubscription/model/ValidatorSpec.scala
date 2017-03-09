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

package uk.gov.hmrc.agentsubscription.model

import play.api.data.validation.ValidationError
import play.api.libs.json.{JsError, JsString, JsSuccess}
import uk.gov.hmrc.play.test.UnitSpec

class ValidatorSpec extends UnitSpec {

  "telephone validator" should {
    "accept input when" when {
      "there are at least 10 digits in the input" in {
        validatePhoneNumber("1234567 ext 123") shouldBe JsSuccess("1234567 ext 123")
      }

      "there are valid symbols in the input" in {
        validatePhoneNumber("+441234567890") shouldBe JsSuccess("+441234567890")
        validatePhoneNumber("#441234567890") shouldBe JsSuccess("#441234567890")
        validatePhoneNumber("(44)1234567890") shouldBe JsSuccess("(44)1234567890")
        validatePhoneNumber("/-*441234567890") shouldBe JsSuccess("/-*441234567890")
      }

      "there is text in the field" in {
        validatePhoneNumber("01234567890 EXT 123") shouldBe JsSuccess("01234567890 EXT 123")
      }

      "there is whitespace in the field" in {
        validatePhoneNumber("0123 456 7890") shouldBe JsSuccess("0123 456 7890")
      }
    }

    "reject input" when {
      "input is empty" in {
        validatePhoneNumber("") shouldBe validationError
      }

      "input is whitespace only" in {
        validatePhoneNumber("   ") shouldBe validationError
      }

      "input contains fewer than 10 digits" in {
        validatePhoneNumber("123456      ") shouldBe validationError
      }

      "input contains more than 24 characters" in {
        validatePhoneNumber("111111111111111111111111aaaaaaaaa") shouldBe JsError(ValidationError("error.maxLength", 24))
      }

    }
  }

  private def validatePhoneNumber(number: String) = {
    telephoneNumber.reads(JsString(number))
  }

  private def validationError = {
    JsError(ValidationError("error.telephone.invalid"))
  }
}
