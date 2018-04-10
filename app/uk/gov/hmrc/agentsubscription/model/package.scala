/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.mvc.{ Request, WrappedRequest }
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.agentsubscription.auth.{ Authority, Enrolment }
import uk.gov.hmrc.domain.{ Nino, TaxIdentifier }

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
      filter[String](ValidationError("error.address.invalid"))(_.matches(nameAndAddressRegex(nameMax)))
  }
}

case class RequestWithAuthority[+A](authority: Authority, request: Request[A]) extends WrappedRequest[A](request)

case class RequestWithEnrolments[+A](enrolments: List[Enrolment], request: Request[A]) extends WrappedRequest[A](request)