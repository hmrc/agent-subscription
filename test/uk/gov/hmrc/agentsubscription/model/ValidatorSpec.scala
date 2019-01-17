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

import play.api.data.validation.ValidationError
import play.api.libs.json.{ JsError, JsString, JsSuccess }
import uk.gov.hmrc.play.test.UnitSpec

class ValidatorSpec extends UnitSpec {

  "telephoneNumberValidation Reader" should {
    def validatePhoneNumber(number: String) = telephoneNumberValidation.reads(JsString(number))

    "accept input when" when {
      "there are at least 10 digits in the input" in {
        val telNum10Digits = "12345678911"
        telNum10Digits.length shouldBe 11
        validatePhoneNumber(telNum10Digits) shouldBe JsSuccess(telNum10Digits)
      }

      "there are valid symbols in the input" in {
        validatePhoneNumber("+441234567890") shouldBe JsSuccess("+441234567890")
        validatePhoneNumber("#441234567890") shouldBe JsSuccess("#441234567890")
        validatePhoneNumber("(44)1234567890") shouldBe JsSuccess("(44)1234567890")
        validatePhoneNumber("441234567xxx") shouldBe JsSuccess("441234567xxx")
      }
      "input contains fewer than 10 digits" in {
        validatePhoneNumber("123456      ") shouldBe JsSuccess("123456      ")
      }

      "there is whitespace in the field" in {
        validatePhoneNumber("0123 456 7890") shouldBe JsSuccess("0123 456 7890")
      }
    }

    "reject input" when {
      "there is text in the field" in {
        validatePhoneNumber("01234567890 EXT 123") shouldBe telephoneValidationError
      }

      "input is empty" in {
        validatePhoneNumber("") shouldBe whitespaceValidationError
      }

      "input is whitespace only" in {
        validatePhoneNumber("   ") shouldBe whitespaceValidationError
      }

      "input contains more than 24 characters" in {
        val telNum25Chars = "1111111111111111111111111"
        telNum25Chars.length shouldBe 25
        validatePhoneNumber(telNum25Chars) shouldBe telephoneValidationError
      }
    }
  }

  "overseasTelephoneNumberValidation reader" should {
    def validateOSNumber(number: String) = overseasTelephoneNumberValidation.reads(JsString(number))

    "accept input" when {
      "there is at least 1 digit in the input" in {
        validateOSNumber("4") shouldBe JsSuccess("4")
      }
      "input contains 24 or fewer digits" in {
        val telNum24Chars = "123456789012345678901234"
        telNum24Chars.length shouldBe 24
        validateOSNumber(telNum24Chars) shouldBe JsSuccess(telNum24Chars)
      }

      "contains letters" in {
        validateOSNumber("ABCDEFGHIJKLM") shouldBe JsSuccess("ABCDEFGHIJKLM")
        validateOSNumber("NOPQRSTUVWXYZ") shouldBe JsSuccess("NOPQRSTUVWXYZ")
      }

      "contains numbers" in {
        validateOSNumber("0123456789") shouldBe JsSuccess("0123456789")
      }

      "contains opening ( bracket" in {
        validateOSNumber("123(456") shouldBe JsSuccess("123(456")
      }

      "contains closing ) bracket" in {
        validateOSNumber("123)456") shouldBe JsSuccess("123)456")
      }

      "contains forward slash /" in {
        validateOSNumber("123/456") shouldBe JsSuccess("123/456")
      }

      "contains hyphen -" in {
        validateOSNumber("123-456") shouldBe JsSuccess("123-456")
      }

      "contains hyphen *" in {
        validateOSNumber("123*456") shouldBe JsSuccess("123*456")
      }

      "contains hash #" in {
        validateOSNumber("123#456") shouldBe JsSuccess("123#456")
      }

      "there is whitespace in the field" in {
        validateOSNumber("0123 456 7890") shouldBe JsSuccess("0123 456 7890")
      }
    }

    "reject input" when {
      "input is empty" in {
        validateOSNumber("") shouldBe whitespaceValidationError
      }

      "input is whitespace only" in {
        validateOSNumber("   ") shouldBe whitespaceValidationError
      }

      "input contains more than 24 characters" in {
        val telNum25Chars = "1234567890123456789012345"
        telNum25Chars.length shouldBe 25
        validateOSNumber(telNum25Chars) shouldBe telephoneValidationError
      }

      "input contains invalid symbols" in {
        validateOSNumber("+441234567890") shouldBe telephoneValidationError
      }
    }
  }

  "postcodeValidation" should {
    def validatePostcode(input: String) = postcodeValidation.reads(JsString(input))

    "accept valid postcodes" in {
      validatePostcode("AA1 1AA") shouldBe JsSuccess("AA1 1AA")
      validatePostcode("AA1M 1AA") shouldBe JsSuccess("AA1M 1AA")
      validatePostcode("A1M 1AA") shouldBe JsSuccess("A1M 1AA")
      validatePostcode("A11 1AA") shouldBe JsSuccess("A11 1AA")
      validatePostcode("AA11 1AA") shouldBe JsSuccess("AA11 1AA")
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

    "reject postcodes where there is whitespace" in {
      validatePostcode("   ") shouldBe postcodeValidationError
    }

    "accept postcodes without spaces" in {
      validatePostcode("AA11AA") shouldBe JsSuccess("AA11AA")
    }

    "accept postcodes with extra spaces" in {
      validatePostcode(" A A 1 1 A A ") shouldBe JsSuccess(" A A 1 1 A A ")
    }
  }

  "addressValidation" should {
    def validateAddress(address: String) = addressValidation.reads(JsString(address))
    def addressValidationError = JsError(ValidationError("error.address.invalid"))

    "accept address" when {

      "a valid address is provided" in {
        validateAddress("Building and Street") shouldBe JsSuccess("Building and Street")
      }

      "address with numbers is provided" in {
        validateAddress("32 Agency Lane") shouldBe JsSuccess("32 Agency Lane")
      }

      "there are valid characters" in {
        validateAddress("Agency's Building/Castle") shouldBe JsSuccess("Agency's Building/Castle")
      }

    }
    "reject address" when {
      "there is whitespace" in {
        validateAddress("   ") shouldBe whitespaceValidationError
      }

      "string is empty but field is present" in {
        validateAddress("") shouldBe whitespaceValidationError
      }

      "invalid characters are present" in {
        validateAddress("Agency;#Co") shouldBe addressValidationError
      }

      "there are more than 35 characters" in {
        val address36Chars = "123456789112345678921234567893123456"
        address36Chars.length shouldBe 36
        validateAddress(address36Chars) shouldBe addressValidationError
      }
    }
  }

  "overseasAddressValidation" should {
    def validateOSAddress(address: String) = overseasAddressValidation.reads(JsString(address))
    def osAddressValidationError = JsError(ValidationError("error.address.invalid"))

    "accept address" when {

      "a valid address is provided" in {
        validateOSAddress("Building and Street") shouldBe JsSuccess("Building and Street")
      }

      "contains uppercase letters" in {
        validateOSAddress("ABCDEFGHIJKLMNOPQRSTUVWXYZ") shouldBe JsSuccess("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
      }

      "contains lowercase letters" in {
        validateOSAddress("abcdefghijklmnopqrstuvwxyz") shouldBe JsSuccess("abcdefghijklmnopqrstuvwxyz")
      }

      "contains numbers" in {
        validateOSAddress("0123456789") shouldBe JsSuccess("0123456789")
      }

      "contains spaces" in {
        validateOSAddress(" A B C ") shouldBe JsSuccess(" A B C ")
      }

      "contains hyphens -" in {
        validateOSAddress("-") shouldBe JsSuccess("-")
      }

      "contains commas ," in {
        validateOSAddress(",") shouldBe JsSuccess(",")
      }

      "contains full stops ." in {
        validateOSAddress(".") shouldBe JsSuccess(".")
      }

      "contains ampersands &" in {
        validateOSAddress("&") shouldBe JsSuccess("&")
      }

      "contains single quotes '" in {
        validateOSAddress("'") shouldBe JsSuccess("'")
      }

      "there are 1 or more characters" in {
        validateOSAddress("1") shouldBe JsSuccess("1")
      }

      "there are 35 or fewer characters" in {
        val address35Chars = "12345678901234567890123456789012345"
        address35Chars.length shouldBe 35
        validateOSAddress(address35Chars) shouldBe JsSuccess(address35Chars)
      }
    }

    "reject address" when {
      "there is only whitespace" in {
        validateOSAddress("   ") shouldBe whitespaceValidationError
      }

      "string is empty but field is present" in {
        validateOSAddress("") shouldBe whitespaceValidationError
      }

      "invalid characters are present, such as a backspace" in {
        validateOSAddress("Acme\\Agency") shouldBe osAddressValidationError
      }

      "there are 36 or more characters" in {
        val address36Chars = "123456789012345678901234567890123456"
        address36Chars.length shouldBe 36
        validateOSAddress(address36Chars) shouldBe osAddressValidationError
      }
    }
  }

  "nameValidation" should {
    def validateName(name: String) = nameValidation.reads(JsString(name))
    def nameValidationError = JsError(ValidationError("error.name.invalid"))

    "accept name" when {
      "a valid name is provided" in {
        validateName("Agency") shouldBe JsSuccess("Agency")
      }

      "valid symbols are provided" in {
        validateName("Agency/firm") shouldBe JsSuccess("Agency/firm")
        validateName("Agency-Co") shouldBe JsSuccess("Agency-Co")
        validateName("Agency,firm,limited") shouldBe JsSuccess("Agency,firm,limited")
      }
    }

    "reject name" when {
      "invalid symbols are provided" in {
        validateName("Agency;Co") shouldBe nameValidationError
        validateName("#1 Agency Worldwide") shouldBe nameValidationError
        validateName("|Agency|") shouldBe nameValidationError
      }
      "an ampersand is provided" in {
        validateName("Agency & Co") shouldBe JsError(ValidationError("error.Ampersand"))
      }

      "name is too long" in {
        validateName("asdfghjklqwertyuiopzxcvbnmqwertyuiopasdfg") shouldBe nameValidationError
      }

      "there is whitespace" in {
        validateName("     ") shouldBe whitespaceValidationError
      }

      "string is empty but field is present" in {
        validateName("") shouldBe whitespaceValidationError
      }
    }
  }

  "overseasNameValidation" should {
    def validateOSName(name: String) = overseasNameValidation.reads(JsString(name))
    def nameValidationError = JsError(ValidationError("error.name.invalid"))

    "accept name" when {
      "a valid name is provided" in {
        validateOSName("Number 1 Foo/Acme Test-Agency, Ltd.") shouldBe JsSuccess("Number 1 Foo/Acme Test-Agency, Ltd.")
      }

      "contains uppercase letters" in {
        validateOSName("ABCDEFGHIJKLMNOPQRSTUVWXYZ") shouldBe JsSuccess("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
      }

      "contains lowercase letters" in {
        validateOSName("abcdefghijklmnopqrstuvwxyz") shouldBe JsSuccess("abcdefghijklmnopqrstuvwxyz")
      }

      "contains numbers" in {
        validateOSName("0123456789") shouldBe JsSuccess("0123456789")
      }

      "contains spaces" in {
        validateOSName(" A B C ") shouldBe JsSuccess(" A B C ")
      }

      "contains hyphens -" in {
        validateOSName("-") shouldBe JsSuccess("-")
      }

      "contains commas ," in {
        validateOSName(",") shouldBe JsSuccess(",")
      }

      "contains full stops ." in {
        validateOSName(".") shouldBe JsSuccess(".")
      }

      "contains forward slashes /" in {
        validateOSName("/") shouldBe JsSuccess("/")
      }

      "1 or more characters" in {
        validateOSName("1") shouldBe JsSuccess("1")
      }

      "40 or fewer characters" in {
        val name40Chars = "1234567890123456789012345678901234567890"
        name40Chars.length shouldBe 40
        validateOSName(name40Chars) shouldBe JsSuccess(name40Chars)
      }
    }

    "reject name" when {
      "contains ampersands &" in {
        validateOSName("&") shouldBe nameValidationError
      }

      "contains single quotes '" in {
        validateOSName("'") shouldBe nameValidationError
      }

      "contains backslashes \\" in {
        validateOSName("\\") shouldBe nameValidationError
      }

      "contains other invalid symbols" in {
        validateOSName("Agency;Co") shouldBe nameValidationError
        validateOSName("#1 Agency Worldwide") shouldBe nameValidationError
        validateOSName("|Agency|") shouldBe nameValidationError
      }

      "more than 40 characters" in {
        val name41Chars = "12345678901234567890123456789012345678901"
        name41Chars.length shouldBe 41
        validateOSName(name41Chars) shouldBe nameValidationError
      }

      "contains only whitespace" in {
        validateOSName("     ") shouldBe whitespaceValidationError
      }

      "string is empty but field is present" in {
        validateOSName("") shouldBe whitespaceValidationError
      }
    }
  }

  "overseasEmailValidation" should {
    def validateOSEmail(email: String) = overseasEmailValidation.reads(JsString(email))
    def emailValidationError = JsError(ValidationError("error.email.invalid"))

    "accept name" when {
      "simple email address" in {
        validateOSEmail("test@example.com") shouldBe JsSuccess("test@example.com")
      }

      "email is minimal" in {
        validateOSEmail("a@b.to") shouldBe JsSuccess("a@b.to")
      }

      "contains lowercase characters" in {
        validateOSEmail("abcdefghijklmnopqrstuvwxyz@c.d") shouldBe JsSuccess("abcdefghijklmnopqrstuvwxyz@c.d")
      }

      "contains uppercase characters" in {
        validateOSEmail("ABCDEFGHIJKLMNOPQRSTUVWXYZ@c.d") shouldBe JsSuccess("ABCDEFGHIJKLMNOPQRSTUVWXYZ@c.d")
      }

      "contains numbers" in {
        validateOSEmail("123@c.d") shouldBe JsSuccess("123@c.d")
      }

      "local-part contains hyphens" in {
        validateOSEmail("a-b@c.d") shouldBe JsSuccess("a-b@c.d")
      }

      "local-part contains full stops" in {
        validateOSEmail("a.b@c.d") shouldBe JsSuccess("a.b@c.d")
      }

      "local-part contains +" in {
        validateOSEmail("a+b@c.d") shouldBe JsSuccess("a+b@c.d")
      }

      "local-part contains #" in {
        validateOSEmail("a#b@c.d") shouldBe JsSuccess("a#b@c.d")
      }
    }

    "reject name" when {
      "missing local-part" in {
        validateOSEmail("@b.com") shouldBe emailValidationError
      }

      "missing domain" in {
        validateOSEmail("a@.com") shouldBe emailValidationError
      }

      "longer than 132 characters" in {
        val email133CharsLong = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa@b.com"
        email133CharsLong.length shouldBe 133

        validateOSEmail(email133CharsLong) shouldBe emailValidationError
      }
    }
  }

  "safeIdValidation" should {
    def validateSafeId(safeId: String) = safeIdValidation.reads(JsString(safeId))
    def safeIdValidationError = JsError(ValidationError("error.safeid.invalid"))

    "accept SafeId" when {
      "it's a perfectly normal SafeID" in {
        val validSafeId = "XE0001234567890"
        validateSafeId(validSafeId) shouldBe JsSuccess(validSafeId)
      }

      "second character is any uppercase letter" in {
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ".foreach { secondCharacter =>
          val validSafeId = s"X${secondCharacter}0001234567890"
          validateSafeId(validSafeId) shouldBe JsSuccess(validSafeId)
        }
      }
    }

    "reject SafeId" when {
      "contains lowercase characters" in {
        val invalidSafeId = "xe0001234567890"
        validateSafeId(invalidSafeId) shouldBe safeIdValidationError
      }

      "first character is not 'X'" in {
        val invalidSafeId = "YE0001234567890"
        validateSafeId(invalidSafeId) shouldBe safeIdValidationError
      }

      "second character is not a letter" in {
        val invalidSafeId = "X00001234567890"
        validateSafeId(invalidSafeId) shouldBe safeIdValidationError
      }

      "3rd - 5th characters are not '000'" in {
        val validSafeIds = for {
          third <- 0 to 9
          fourth <- 0 to 9
          fifth <- 0 to 9
          if (0 != (third + fourth + fifth))
        } yield s"X$third$fourth${fifth}1234567890"

        validSafeIds.foreach { validSafeId =>
          validateSafeId(validSafeId) shouldBe safeIdValidationError
        }
      }

      "one of the characters in the last numeric portion (i.e. the last 10 characters) is not numeric (0-9)" in {
        validateSafeId("XE000X234567890") shouldBe safeIdValidationError
        validateSafeId("XE0001X34567890") shouldBe safeIdValidationError
        validateSafeId("XE00012X4567890") shouldBe safeIdValidationError
        validateSafeId("XE000123X567890") shouldBe safeIdValidationError
        validateSafeId("XE0001234X67890") shouldBe safeIdValidationError
        validateSafeId("XE00012345X7890") shouldBe safeIdValidationError
        validateSafeId("XE000123456X890") shouldBe safeIdValidationError
        validateSafeId("XE0001234567X90") shouldBe safeIdValidationError
        validateSafeId("XE00012345678X0") shouldBe safeIdValidationError
        validateSafeId("XE000123456789X") shouldBe safeIdValidationError
      }

      "length is less than 15" in {
        val safeId14Chars = "XE000123456789"
        safeId14Chars.length shouldBe 14
        validateSafeId(safeId14Chars) shouldBe safeIdValidationError
      }

      "length is greater than 15" in {
        val safeId16Chars = "XE00012345678901"
        safeId16Chars.length shouldBe 16
        validateSafeId(safeId16Chars) shouldBe safeIdValidationError
      }
    }
  }

  private def whitespaceValidationError = {
    JsError(ValidationError("error.whitespace.or.empty"))
  }
  private def postcodeValidationError = {
    JsError(ValidationError("error.postcode.invalid"))
  }

  private def telephoneValidationError = {
    JsError(ValidationError("error.telephone.invalid"))
  }
}
