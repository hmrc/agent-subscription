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

package uk.gov.hmrc.agentsubscription

import play.api.libs.functional.syntax._
import play.api.libs.json.JsonValidationError
import play.api.libs.json.Reads._
import play.api.mvc.{ Request, WrappedRequest }
import uk.gov.hmrc.agentsubscription.auth.{ Authority, Enrolment }

package object model {
  val postcodeWithoutSpacesRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$"
  val telephoneRegex = "^[0-9- +()#x ]{0,24}$"
  val noAmpersand = "[^&]*"
  val addressMax = 35
  val nameMax = 40
  def nameAndAddressRegex(max: Int) = s"^[A-Za-z0-9 \\-,.&'\\/]{0,$max}$$"

  private[model] val telephoneNumberValidation = {
    filterNot[String](JsonValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](JsonValidationError("error.telephone.invalid"))(_.matches(telephoneRegex))
  }

  private[model] val postcodeValidation = {
    filter[String](JsonValidationError("error.postcode.invalid"))(_.replaceAll("\\s", "").matches(postcodeWithoutSpacesRegex))
  }

  private[model] val addressValidation = {
    filterNot[String](JsonValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](JsonValidationError("error.address.invalid"))(_.matches(nameAndAddressRegex(addressMax)))
  }

  private[model] val nameValidation = {
    filterNot[String](JsonValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](JsonValidationError("error.Ampersand"))(_.matches(noAmpersand)) andKeep
      filter[String](JsonValidationError("error.name.invalid"))(_.matches(nameAndAddressRegex(nameMax)))
  }

  private[model] val overseasAddressValidation = {
    val overseasAddressRegex = s"^[A-Za-z0-9 \\-,.&']{0,35}$$"
    filterNot[String](JsonValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](JsonValidationError("error.address.invalid"))(_.matches(overseasAddressRegex))
  }

  private[model] val overseasNameValidation = {
    val overseasAgencyNameRegex = s"^[A-Za-z0-9 \\-,.\\/]{0,40}$$"

    filterNot[String](JsonValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](JsonValidationError("error.name.invalid"))(_.matches(overseasAgencyNameRegex))
  }

  private[model] val overseasCountryCodeValidation = {
    val overseasCountryCodeRegex = "[A-Z]{2}"

    //filter[String](JsonValidationError("error.overseas.countryCode"))(_ != "GB") andKeep
    filter[String](JsonValidationError("error.overseas.countryCode"))(_.matches(overseasCountryCodeRegex))
  }

  private[model] val ukAddressForOverseasCountryCodeValidation = {
    val greatBritainCountryCode = "GB"

    filterNot[String](JsonValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](JsonValidationError("error.overseas.countryCode"))(_.matches(greatBritainCountryCode))
  }

  private[model] val ukAddressForOverseasPostalCodeValidation = {
    val postalCodePattern = """^[A-Z]{1,2}[0-9][0-9A-Z]?\s?[0-9][A-Z]{2}|BFPO\s?[0-9]{1,10}$"""

    filterNot[String](JsonValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](JsonValidationError("error.overseas.ukAddressForOverseasPostalCode"))(_.matches(postalCodePattern))
  }

  private[model] val overseasTelephoneNumberValidation = {
    val overseasPhoneRegex = s"^[A-Z0-9 )\\/(\\-*#]{0,24}$$"

    filterNot[String](JsonValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](JsonValidationError("error.telephone.invalid"))(_.matches(overseasPhoneRegex))
  }

  private[model] val overseasEmailValidation = {
    val overseasEmailRegex = """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"""

    filterNot[String](JsonValidationError("error.email.invalid"))(_.length > 132) andKeep
      filter[String](JsonValidationError("error.email.invalid"))(_.matches(overseasEmailRegex))
  }

  private[model] val safeIdValidation = {
    val safeIdRegex = """^X[A-Z]000[0-9]{10}$"""

    filter[String](JsonValidationError("error.safeid.invalid"))(_.matches(safeIdRegex))
  }

  private[model] val crnValidation = {
    val crnLength = 8
    val crnRegex = "[A-Z]{2}[0-9]{6}|[0-9]{8}"

    filterNot[String](JsonValidationError("error.crn.invalid"))(_.length == crnLength) andKeep
      filter[String](JsonValidationError("error.crn.invalid"))(_.matches(crnRegex))
  }
}

case class RequestWithAuthority[+A](authority: Authority, request: Request[A]) extends WrappedRequest[A](request)

case class RequestWithEnrolments[+A](enrolments: List[Enrolment], request: Request[A]) extends WrappedRequest[A](request)