package uk.gov.hmrc.agentsubscription.controllers

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{AUTHORIZATION, CONTENT_TYPE}
import uk.gov.hmrc.agentsubscription.model.{DateOfBirth, DesignatoryDetails}
import uk.gov.hmrc.agentsubscription.model.DesignatoryDetails.Person
import uk.gov.hmrc.agentsubscription.stubs.{AuthStub, CitizenDetailsStubs}
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.domain.Nino

import scala.concurrent.Await
import scala.concurrent.duration._

class CitizenDetailsControllerISpec extends BaseISpec with CitizenDetailsStubs with AuthStub {

  implicit val ws = app.injector.instanceOf[WSClient]

  val duration = Duration(5, SECONDS)

  val nino = Nino("XX121212B")
  val dobString = "1900-01-01"
  val dtf = DateTimeFormatter.ofPattern("yyyy-MM-DD")
  val dob = DateOfBirth(LocalDate.parse(dobString, dtf))

  def doRequest(nino: Nino) =
    Await.result(
      ws.url(s"http://localhost:$port/agent-subscription/citizen-details/${nino.value}/designatory-details")
        .withHttpHeaders(CONTENT_TYPE -> "application/json")
        .withHttpHeaders(AUTHORIZATION -> "Bearer XYZ")
        .get(),
      duration
    )

  "GET /citizen-details/${nino}/designatory-details" should {
    "return 200 when nino is found in Citizen details and the dob returned matches" in {
      requestIsAuthenticatedWithNoEnrolments()
      givencitizenDetailsFoundForNino(nino.value, dobString, Some("Matchmaker"))

      val response = doRequest(nino)
      response.status shouldBe 200

      response.json.as[DesignatoryDetails] shouldBe DesignatoryDetails(
        Some(Person(Some("Matchmaker"), Some(dob), deceased = Some(false)))
      )
    }

    "return 404 when DesignatoryDetails are not found for a passed in nino" in {
      requestIsAuthenticatedWithNoEnrolments()
      givenCitizenDetailsNotFoundForNino(nino.value)

      val response = doRequest(nino)
      response.status shouldBe 404
    }

  }

}
