package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.stubs.TaxEnrolmentsStubs
import uk.gov.hmrc.agentsubscription.support.{ MetricsTestSupport, WireMockSupport }
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class TaxEnrolmentsConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with TaxEnrolmentsStubs with MetricsTestSupport with MockitoSugar {
  private lazy val wiremockUrl = new URL(s"http://localhost:$wireMockPort")
  private lazy val auditConnector = mock[AuditConnector]
  private lazy val httpVerbs = app.injector.instanceOf[HttpPut with HttpPost with HttpGet with HttpDelete]
  private lazy val connector = new TaxEnrolmentsConnector(wiremockUrl, wiremockUrl, httpVerbs, app.injector.instanceOf[Metrics])

  private implicit val hc = HeaderCarrier()
  private val arn = Arn("AARN1234567")
  private val postcode = "SY12 8RN"
  private val knownFactKey = "TestKnownFactKey"

  val groupId = "groupId"

  "create known facts" should {
    "return status 200 after successfully creating known facts" in {
      givenCleanMetricRegistry()
      createKnownFactsSucceeds(arn.value)
      val result = await(connector.addKnownFacts(arn.value, knownFactKey, postcode))
      result shouldBe 200
      verifyTimerExistsAndBeenUpdated("EMAC-AddKnownFacts-HMRC-AS-AGENT-PUT")
    }

    "propagate an exception after failing to create known facts" in {
      createKnownFactsFails(arn.value)

      val exception = intercept[Upstream5xxResponse] {
        await(connector.addKnownFacts(arn.value, knownFactKey, postcode))
      }
      exception.upstreamResponseCode shouldBe 500
    }
  }

  "deleteKnownFacts" should {
    "return successfully deleting known facts" in {
      givenCleanMetricRegistry()
      deleteKnownFactsSucceeds(arn.value)
      val result = await(connector.deleteKnownFacts(arn))
      result shouldBe 204
      verifyTimerExistsAndBeenUpdated("EMAC-DeleteKnownFacts-HMRC-AS-AGENT-DELETE")
    }

    "propagate an exception after failing to delete known facts" in {
      deleteKnownFactsFails(arn.value)

      val exception = intercept[Upstream5xxResponse] {
        await(connector.deleteKnownFacts(arn))
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
      val result = await(connector.enrol(groupId, arn, enrolmentRequest))
      result shouldBe 200
      verifyTimerExistsAndBeenUpdated("EMAC-Enrol-HMRC-AS-AGENT-POST")
    }

    "propagate an exception for a failed enrolment" in {
      enrolmentFails(groupId, arn.value)

      val exception = intercept[Upstream5xxResponse] {
        await(connector.enrol(groupId, arn, enrolmentRequest))
      }

      exception.upstreamResponseCode shouldBe 500
    }
  }

  "hasPrincipalGroupIds" should {

    "return true if a successful query for principal enrolments returns some groups" in {
      givenCleanMetricRegistry()
      allocatedPrincipalEnrolmentExists(arn.value, groupId)
      val result = await(connector.hasPrincipalGroupIds(arn))
      result shouldBe true
      verifyTimerExistsAndBeenUpdated("EMAC-GetPrincipalGroupIdFor-HMRC-AS-AGENT-GET")
    }

    "return false after a successful query for principal enrolment" in {
      givenCleanMetricRegistry()
      allocatedPrincipalEnrolmentNotExists(arn.value)
      val result = await(connector.hasPrincipalGroupIds(arn))
      result shouldBe false
      verifyTimerExistsAndBeenUpdated("EMAC-GetPrincipalGroupIdFor-HMRC-AS-AGENT-GET")
    }

    "propagate an exception for a failed query" when {
      "failed with 500" in {
        allocatedPrincipalEnrolmentFails(arn.value, 500)

        val exception = intercept[Upstream5xxResponse] {
          await(connector.hasPrincipalGroupIds(arn))
        }

        exception.upstreamResponseCode shouldBe 500
      }
      "failed with 400" in {
        allocatedPrincipalEnrolmentFails(arn.value, 400)

        val exception = intercept[BadRequestException] {
          await(connector.hasPrincipalGroupIds(arn))
        }

        exception.responseCode shouldBe 400
      }
    }

  }

}
