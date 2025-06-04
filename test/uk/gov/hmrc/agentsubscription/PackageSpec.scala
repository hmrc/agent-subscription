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

package uk.gov.hmrc.agentsubscription

import org.scalatest.funsuite.AnyFunSuite

class PackageSpec
extends AnyFunSuite {

  test("Postcode matcher should return false when postcodes are not the same") {
    val postcode1 = "AB1 1BA"
    val postcode2 = "CD11DC"
    assert(!postcodesMatch(postcode1, postcode2))
  }

  test(
    "Postcode matcher should return true when postcodes are the same but different due to the case of the letters in the postcode"
  ) {
    val postcode1 = "AB1 1BA"
    val postcode2 = "ab1 1ba"
    assert(postcodesMatch(postcode1, postcode2))
  }

  test("Postcode matcher should return true when both postcodes are in uppercase") {
    val postcode1 = "BN1 2ZB"
    val postcode2 = "BN1 2ZB"
    assert(postcodesMatch(postcode1, postcode2))
  }

  test(
    "Postcode matcher should return true when both postcodes are the same but differ due to spacing between components of the postcode"
  ) {
    val postcode1 = "MN11KL"
    val postcode2 = "MN1 1KL"
    assert(postcodesMatch(postcode1, postcode2))
  }

  test(
    "Postcode matcher should return true when both postcodes are the same and have no spaces between their letters"
  ) {
    val postcode1 = "LO11OL"
    val postcode2 = "LO11OL"
    assert(postcodesMatch(postcode1, postcode2))
  }

}
