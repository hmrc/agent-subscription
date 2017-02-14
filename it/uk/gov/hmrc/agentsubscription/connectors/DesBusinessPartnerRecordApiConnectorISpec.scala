package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscription.WSHttp
import uk.gov.hmrc.agentsubscription.model.{BusinessPartnerRecordFound, BusinessPartnerRecordNotFound, DesBusinessPartnerRecordApiResponse}
import uk.gov.hmrc.agentsubscription.stubs.DesStubs.{findMatchForUtrForASAgent, noMatchForUtr, utrIsInvalid}
import uk.gov.hmrc.agentsubscription.support.WireMockSupport
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class DesBusinessPartnerRecordApiConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport {
  private implicit val hc = HeaderCarrier()

  private lazy val connector: DesBusinessPartnerRecordApiConnector =
    new HttpDesBusinessPartnerRecordApiConnector(new URL(s"http://localhost:${wireMockPort}"), "auth-token", "des-env", WSHttp)

  "DES Subscription Connector" should {
    "return subscription details for an agent matching UTR" in {
      findMatchForUtrForASAgent()
      val result: DesBusinessPartnerRecordApiResponse = await(connector.getBusinessPartnerRecord("0123456789"))
      result shouldBe BusinessPartnerRecordFound("BN12 4SE", true)
    }

    "return not found status for a not found UTR" in {
      noMatchForUtr()
      val result: DesBusinessPartnerRecordApiResponse = await(connector.getBusinessPartnerRecord("0000000000"))
      result shouldBe BusinessPartnerRecordNotFound
    }

    "return not found status for an invalid UTR" in {
      utrIsInvalid()
      val result: DesBusinessPartnerRecordApiResponse = await(connector.getBusinessPartnerRecord("xyz"))
      result shouldBe BusinessPartnerRecordNotFound
    }
  }
}
