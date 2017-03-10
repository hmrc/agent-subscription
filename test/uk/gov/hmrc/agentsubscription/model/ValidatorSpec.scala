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
        validatePhoneNumber("") shouldBe telephoneValidationError
      }

      "input is whitespace only" in {
        validatePhoneNumber("   ") shouldBe telephoneValidationError
      }

      "input contains fewer than 10 digits" in {
        validatePhoneNumber("123456      ") shouldBe telephoneValidationError
      }

      "input contains more than 24 characters" in {
        validatePhoneNumber("111111111111111111111111aaaaaaaaa") shouldBe JsError(ValidationError("error.maxLength", 24))
      }

    }
  }

  "postcode validator" should {
    "accept valid postcodes" in {
      validatePostcode("AA1 1AA") shouldBe JsSuccess("AA1 1AA")
      validatePostcode("AA1M 1AA") shouldBe JsSuccess("AA1M 1AA")
    }

    "give \"error.required\" error when it is empty" in {
      validatePostcode("") shouldBe postcodeValidationError
    }

    "give \"error.required\" error when it only contains a space" in {
      validatePostcode("  ") shouldBe postcodeValidationError
    }

    "reject postcodes containing invalid characters" in {
      validatePostcode("_A1 1AA") shouldBe postcodeValidationError
      validatePostcode("A.1 1AA") shouldBe postcodeValidationError
      validatePostcode("AA/ 1AA") shouldBe postcodeValidationError
      validatePostcode("AA1#1AA") shouldBe postcodeValidationError
      validatePostcode("AA1 ~AA") shouldBe postcodeValidationError
      validatePostcode("AA1 1$A") shouldBe postcodeValidationError
      validatePostcode("AA1 1A%") shouldBe postcodeValidationError
    }

    "accept lower case postcodes" in {
      validatePostcode("aa1 1aa") shouldBe JsSuccess("aa1 1aa")
    }

    "accept postcodes with 2 characters in the outbound part" in {
      validatePostcode("A1 1AA") shouldBe JsSuccess("A1 1AA")
    }

    "accept postcodes with 4 characters in the outbound part" in {
      validatePostcode("AA1A 1AA") shouldBe JsSuccess("AA1A 1AA")
    }

    "reject postcodes where the 1st character of the outbound part is a number" in {
      validatePostcode("1A1 1AA") shouldBe postcodeValidationError
    }

    "reject postcodes where the length of the inbound part is not 3" in {
      validatePostcode("AA1 1A") shouldBe postcodeValidationError
      validatePostcode("AA1 1AAA") shouldBe postcodeValidationError
    }

    "reject postcodes where the 1st character of the inbound part is a letter" in {
      validatePostcode("AA1 AAA") shouldBe postcodeValidationError
    }

    "accept postcodes without spaces" in {
      validatePostcode("AA11AA") shouldBe JsSuccess("AA11AA")
    }

    "accept postcodes with extra spaces" in {
      validatePostcode(" A A 1 1 A A ") shouldBe JsSuccess(" A A 1 1 A A ")
    }
  }

  private def validatePostcode(input: String) = {
    postcode.reads(JsString(input))
  }

  private def validatePhoneNumber(number: String) = {
    telephoneNumber.reads(JsString(number))
  }

  private def postcodeValidationError = {
    JsError(ValidationError("error.postcode.invalid"))
  }

  private def telephoneValidationError = {
    JsError(ValidationError("error.telephone.invalid"))
  }
}
