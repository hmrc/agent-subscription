package uk.gov.hmrc.agentsubscription.connectors

import java.time.LocalDate

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.{ AmlsDetails, OverseasAmlsDetails, RegisteredDetails }
import uk.gov.hmrc.agentsubscription.stubs.AgentAssuranceStub
import uk.gov.hmrc.agentsubscription.support.{ BaseISpec, MetricsTestSupport }
import uk.gov.hmrc.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global

class AgentAssuranceConnectorISpec extends BaseISpec with AgentAssuranceStub with MetricsTestSupport with MockitoSugar {

  val utr = Utr("7000000002")
  val arn = Arn("TARN0000001")

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val http: HttpClient = app.injector.instanceOf[HttpClient]
  private lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  private lazy val connector: AgentAssuranceConnector = new AgentAssuranceConnectorImpl(appConfig, http, metrics)

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
