package uk.gov.hmrc.agentsubscription

import org.scalatest.FunSuite

class PackageSpec extends FunSuite {

  test("Postcode matcher should return false when postcodes are not the same"){
    val postcode1 = "AB1 1BA"
    val postcode2 = "CD11DC"
    assert(!postcodesMatch(Some(postcode1), postcode2))
  }

  test("Postcode matcher should return true when postcodes are the same but different due to the case of the letters in the postcode") {
    val postcode1 = "AB1 1BA"
    val postcode2 = "ab1 1ba"
    assert(postcodesMatch(Some(postcode1), postcode2))
  }

  test("Postcode matcher should return true when both postcodes are in uppercase") {
    val postcode1 = "BN1 2ZB"
    val postcode2 = "BN1 2ZB"
    assert(postcodesMatch(Some(postcode1), postcode2))
  }

  test("Postcode matcher should return true when both postcodes are the same but differ due to spacing between components of the postcode") {
    val postcode1 = "MN11KL"
    val postcode2 = "MN1 1KL"
    assert(postcodesMatch(Some(postcode1), postcode2))
  }

  test("Postcode matcher should return true when both postcodes are the same and have no spaces between their letters") {
    val postcode1 = "LO11OL"
    val postcode2 = "LO11OL"
    assert(postcodesMatch(Some(postcode1), postcode2))
  }

}
