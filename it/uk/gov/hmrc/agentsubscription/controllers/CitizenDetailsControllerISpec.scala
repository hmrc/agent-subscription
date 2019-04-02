package uk.gov.hmrc.agentsubscription.controllers

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.test.Helpers.CONTENT_TYPE
import uk.gov.hmrc.agentsubscription.model.{ CitizenDetailsRequest, DateOfBirth }
import uk.gov.hmrc.agentsubscription.stubs.{ AuthStub, CitizenDetailsStubs }
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.domain.Nino

import scala.concurrent.Await
import scala.concurrent.duration._

class CitizenDetailsControllerISpec extends BaseISpec with CitizenDetailsStubs with AuthStub {

  implicit val ws = app.injector.instanceOf[WSClient]

  val duration = Duration(5, SECONDS)

  val checkCitizenDetailsUrl = s"http://localhost:$port/agent-subscription/citizen-details"

  val nino = Nino("XX121212B")
  val dobString = "12121900"
  val dtf = DateTimeFormatter.ofPattern("ddMMyyyy")
  val dob = DateOfBirth(LocalDate.parse(dobString, dtf))
  val citizenDetailsRequest = CitizenDetailsRequest(nino, dob)

  def doRequest(request: CitizenDetailsRequest) =
    Await.result(
      ws.url(checkCitizenDetailsUrl)
        .withHeaders(CONTENT_TYPE -> "application/json")
        .post(Json.toJson(request)), duration)

  "POST /citizen-details" should {
    "return 200 when nino is found in Citizen details and the dob returned matches" in {
      requestIsAuthenticatedWithNoEnrolments()
      givencitizenDetailsFoundForNino(nino.value, dobString)

      val response = doRequest(citizenDetailsRequest)
      response.status shouldBe 200
    }

    "return 404 when nino is found in citizen details but the dob returned does not match" in {
      requestIsAuthenticatedWithNoEnrolments()
      givencitizenDetailsFoundForNino(nino.value, dobString)

      val response = doRequest(citizenDetailsRequest.copy(dateOfBirth = DateOfBirth(LocalDate.now)))
      response.status shouldBe 404
    }

    "return 404 when nino was not found in citizen details" in {
      requestIsAuthenticatedWithNoEnrolments()
      givenCitizenDetailsNotFoundForNino(nino.value)

      val response = doRequest(citizenDetailsRequest)
      response.status shouldBe 404
    }

  }

}
