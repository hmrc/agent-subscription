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

package uk.gov.hmrc.agentsubscription

import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
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
    filterNot[String](ValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](ValidationError("error.telephone.invalid"))(_.matches(telephoneRegex))
  }

  private[model] val postcodeValidation = {
    filter[String](ValidationError("error.postcode.invalid"))(_.replaceAll("\\s", "").matches(postcodeWithoutSpacesRegex))
  }

  private[model] val addressValidation = {
    filterNot[String](ValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](ValidationError("error.address.invalid"))(_.matches(nameAndAddressRegex(addressMax)))
  }

  private[model] val nameValidation = {
    filterNot[String](ValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](ValidationError("error.Ampersand"))(_.matches(noAmpersand)) andKeep
      filter[String](ValidationError("error.name.invalid"))(_.matches(nameAndAddressRegex(nameMax)))
  }

  private[model] val overseasAddressValidation = {
    val overseasAddressRegex = s"^[A-Za-z0-9 \\-,.&']{0,35}$$"

    filterNot[String](ValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](ValidationError("error.address.invalid"))(_.matches(overseasAddressRegex))
  }

  private[model] val overseasNameValidation = {
    val overseasAgencyNameRegex = s"^[A-Za-z0-9 \\-,.\\/]{0,40}$$"

    filterNot[String](ValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](ValidationError("error.name.invalid"))(_.matches(overseasAgencyNameRegex))
  }

  private[model] val overseasCountryCodeValidation = {
    val overseasCountryCodeRegex = "[A-Z]{2}"

    filter[String](ValidationError("error.overseas.countryCode"))(_ != "GB") andKeep
      filter[String](ValidationError("error.overseas.countryCode"))(_.matches(overseasCountryCodeRegex))
  }
  private[model] val overseasTelephoneNumberValidation = {
    val overseasPhoneRegex = s"^[A-Z0-9 )\\/(\\-*#]{0,24}$$"

    filterNot[String](ValidationError("error.whitespace.or.empty"))(_.replaceAll("\\s", "").isEmpty) andKeep
      filter[String](ValidationError("error.telephone.invalid"))(_.matches(overseasPhoneRegex))
  }

  private[model] val overseasEmailValidation = {
    val overseasEmailRegex = """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"""

    filterNot[String](ValidationError("error.email.invalid"))(_.length > 132) andKeep
      filter[String](ValidationError("error.email.invalid"))(_.matches(overseasEmailRegex))
  }

  private[model] val safeIdValidation = {
    val safeIdRegex = """^X[A-Z]000[0-9]{10}$"""

    filter[String](ValidationError("error.safeid.invalid"))(_.matches(safeIdRegex))
  }
}

case class RequestWithAuthority[+A](authority: Authority, request: Request[A]) extends WrappedRequest[A](request)

case class RequestWithEnrolments[+A](enrolments: List[Enrolment], request: Request[A]) extends WrappedRequest[A](request)