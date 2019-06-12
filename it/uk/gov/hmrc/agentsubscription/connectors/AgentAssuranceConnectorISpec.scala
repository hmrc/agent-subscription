package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL
import java.time.LocalDate

import com.kenshoo.play.metrics.Metrics
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription.model.{ AmlsDetails, OverseasAmlsDetails, RegisteredDetails }
import uk.gov.hmrc.agentsubscription.stubs.AgentAssuranceStub
import uk.gov.hmrc.agentsubscription.support.{ MetricsTestSupport, WireMockSupport }
import uk.gov.hmrc.http.{ HttpPost, HttpPut }
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class AgentAssuranceConnectorISpec extends AgentAssuranceStub with UnitSpec with OneAppPerSuite with WireMockSupport with MetricsTestSupport with MockitoSugar {

  val utr = Utr("7000000002")
  val arn = Arn("TARN0000001")

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val http: HttpPut with HttpPost = app.injector.instanceOf[HttpPut with HttpPost]

  private lazy val connector: AgentAssuranceConnector =
    new AgentAssuranceConnectorImpl(new URL(s"http://localhost:$wireMockPort"), http, metrics)

  val amlsDetails: AmlsDetails = AmlsDetails("supervisory", Right(RegisteredDetails("12345", LocalDate.now())))
  val overseasAmlsDetails = OverseasAmlsDetails("supervisory", Some("12345"))

  "creating AMLS" should {
    "return a successful response" in {

      createAmlsSucceeds(utr, amlsDetails)

      val result = await(connector.createAmls(utr, amlsDetails))

      result shouldBe true

    }

    "handle failure responses from agent-assurance backend during create amls" in {

      createAmlsFailsWithStatus(403)

      val result = await(connector.createAmls(utr, amlsDetails))

      result shouldBe false
    }
  }

  "updating AMLS" should {

    "return a successful response" in {

      updateAmlsSucceeds(utr, arn, amlsDetails)

      val result = await(connector.updateAmls(utr, arn))

      result shouldBe Some(amlsDetails)

    }

    "return a None when agent assurance return 404" in {

      updateAmlsFailsWithStatus(utr, arn, 404)

      val result = await(connector.updateAmls(utr, arn))

      result shouldBe None
    }
  }

  "creating Overseas AMLS" should {
    "return a successful response" in {

      createOverseasAmlsSucceeds(arn, overseasAmlsDetails)

      val result = await(connector.createOverseasAmls(arn, overseasAmlsDetails))

      result shouldBe (())
    }

    "handle conflict responses" in {

      createOverseasAmlsFailsWithStatus(409)

      val result = await(connector.createOverseasAmls(arn, overseasAmlsDetails))

      result shouldBe (())
    }

    "handle failure responses" in {

      createOverseasAmlsFailsWithStatus(500)

      an[Exception] should be thrownBy (await(connector.createOverseasAmls(arn, overseasAmlsDetails)))
    }
  }
}
