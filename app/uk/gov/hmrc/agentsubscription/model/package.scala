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

package uk.gov.hmrc.agentsubscription

import play.api.data.validation.ValidationError
import play.api.libs.json.Reads
import play.api.libs.json.Reads.filterNot
import play.api.libs.functional.syntax._

package object model {
  private val postcodeWithoutSpacesRegex = "^[A-Za-z]{1,2}[0-9]{1,2}[A-Za-z]?[0-9][A-Za-z]{2}$"
  private val telephoneNumberMaxLength = 24
  private val minimumTelephoneDigits = 10

  private[model] def nonEmptyStringWithMaxLength(maxLength: Int) = {
    Reads.maxLength[String](maxLength) andKeep filterNot[String](ValidationError("error.whitespace"))(_.replaceAll("\\s", "").isEmpty)
  }

  private[model] def telephoneNumber = {
    Reads.maxLength[String](telephoneNumberMaxLength) andKeep
      filterNot[String](ValidationError("error.telephone.invalid"))(_.replaceAll("[^0-9]", "").length < minimumTelephoneDigits)
  }

  private [model] def postcode = {
    Reads.filter[String](ValidationError("error.postcode.invalid"))(_.replaceAll("\\s", "").matches(postcodeWithoutSpacesRegex))
  }
}
