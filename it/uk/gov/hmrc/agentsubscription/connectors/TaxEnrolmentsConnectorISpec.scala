package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.WSHttp
import uk.gov.hmrc.agentsubscription.stubs.TaxEnrolmentsStubs
import uk.gov.hmrc.agentsubscription.support.{MetricsTestSupport, WireMockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class TaxEnrolmentsConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with TaxEnrolmentsStubs with MetricsTestSupport {

  private lazy val connector = new TaxEnrolmentsConnector(new URL(s"http://localhost:$wireMockPort"), WSHttp, app.injector.instanceOf[Metrics])

  private implicit val hc = HeaderCarrier()
  private val arn = Arn("AARN1234567")
  private val postcode = "SY12 8RN"

  val groupId = "groupId"

  "create known facts" should {
    "return status 200 after successfully creating known facts" in {
      givenCleanMetricRegistry()
      createKnownFactsSucceeds(arn.value)
      val result = await(connector.sendKnownFacts(arn.value,postcode))
      result shouldBe 200
      verifyTimerExistsAndBeenUpdated("EMAC-AddKnownFacts-HMRC-AS-AGENT-POST")
    }

    "propogate an exception after failing to create known facts" in {
      createKnownFactsFails(arn.value)

      val exception = intercept[Upstream5xxResponse] {
        await(connector.sendKnownFacts(arn.value,postcode))
      }
      exception.upstreamResponseCode shouldBe 500
    }
  }

  "addEnrolment" should {
    val enrolmentRequest = EnrolmentRequest("userId", "principal", "friendlyName",
      Seq(KnownFact("AgencyPostcode", "AB11BA")))

    "return status 200 after a successful enrolment" in {
      givenCleanMetricRegistry()
      enrolmentSucceeds(groupId, arn.value)
      val result = await(connector.enrol(groupId,arn,enrolmentRequest))
      result shouldBe 200
      verifyTimerExistsAndBeenUpdated("EMAC-Enrol-HMRC-AS-AGENT-POST")
    }

    "propagate an exception for a failed enrolment" in {
      enrolmentFails(groupId, arn.value)

      val exception = intercept[Upstream5xxResponse] {
        await(connector.enrol(groupId,arn,enrolmentRequest))
      }

      exception.upstreamResponseCode shouldBe 500
    }
  }

}
