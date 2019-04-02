package uk.gov.hmrc.agentsubscription.connectors

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscription.model.DateOfBirth
import uk.gov.hmrc.agentsubscription.stubs.CitizenDetailsStubs
import uk.gov.hmrc.agentsubscription.support.{ MetricsTestSupport, WireMockSupport }
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{ NotFoundException, HeaderCarrier, HttpGet, Upstream5xxResponse }
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext

class CitizenDetailsConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with MetricsTestSupport with CitizenDetailsStubs {

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val http: HttpGet = app.injector.instanceOf[HttpGet]

  val cdConnector = new CitizenDetailsConnectorImpl(wireMockBaseUrl, app.injector.instanceOf[HttpGet], app.injector.instanceOf[Metrics])

  val nino = Nino("XX212121B")
  val dobString = "12121990"
  val dtf = DateTimeFormatter.ofPattern("ddMMyyyy")
  val dob = DateOfBirth(LocalDate.parse(dobString, dtf))

  private implicit val hc = HeaderCarrier()
  private implicit val ec = ExecutionContext.global

  "citizen details connector" should {
    "return a DateOfBirth when given a valid Nino" in {
      givencitizenDetailsFoundForNino(nino.value, dobString)
      await(cdConnector.getDateOfBirth(nino)) shouldBe Some(dob)
    }

    "return NotFound when the Nino was not recognized" in {
      givenCitizenDetailsNotFoundForNino(nino.value)
      val exception = intercept[NotFoundException] {
        await(cdConnector.getDateOfBirth(nino))
      }

      exception.getMessage.contains("") shouldBe true
    }
  }
}
