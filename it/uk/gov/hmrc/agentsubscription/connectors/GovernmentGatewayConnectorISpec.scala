//package uk.gov.hmrc.agentsubscription.connectors
//
//import java.net.URL
//
//import com.kenshoo.play.metrics.Metrics
//import org.scalatestplus.play.OneAppPerSuite
//import uk.gov.hmrc.agentsubscription.WSHttp
//import uk.gov.hmrc.agentsubscription.stubs.GGStubs
//import uk.gov.hmrc.agentsubscription.support.{MetricsTestSupport, WireMockSupport}
//import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
//import uk.gov.hmrc.play.test.UnitSpec
//
//import scala.concurrent.ExecutionContext.Implicits.global
//
//class GovernmentGatewayConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with GGStubs with MetricsTestSupport {
//  private lazy val connector = new GovernmentGatewayConnector(new URL(s"http://localhost:$wireMockPort"), WSHttp,app.injector.instanceOf[Metrics])
//
//  private implicit val hc = HeaderCarrier()
//  private val friendlyName = "Mr Friendly"
//  private val arn = "AARN1234567"
//  private val postcode = "SY12 8RN"
//
//  "addEnrolment" should {
//    "return status 200 after a successful enrolment" in {
//      givenCleanMetricRegistry()
//      enrolmentSucceeds()
//      val result = await(connector.enrol(friendlyName,arn,postcode))
//      result shouldBe 200
//      verifyTimerExistsAndBeenUpdated("GGW-Enrol-HMRC-AS-AGENT-POST")
//    }
//
//    "propagate an exception for a failed enrolment" in {
//      enrolmentFails()
//
//      val exception = intercept[Upstream5xxResponse] {
//        await(connector.enrol(friendlyName,arn,postcode))
//      }
//
//      exception.upstreamResponseCode shouldBe 500
//    }
//  }
//}
