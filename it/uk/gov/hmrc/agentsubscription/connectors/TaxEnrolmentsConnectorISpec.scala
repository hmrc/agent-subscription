package uk.gov.hmrc.agentsubscription.connectors

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.stubs.TaxEnrolmentsStubs
import uk.gov.hmrc.agentsubscription.support.{ BaseISpec, MetricsTestSupport }
import uk.gov.hmrc.http.{ HttpClient, _ }

import scala.concurrent.ExecutionContext.Implicits.global

class TaxEnrolmentsConnectorISpec extends BaseISpec with TaxEnrolmentsStubs with MetricsTestSupport with MockitoSugar {

  private lazy val http = app.injector.instanceOf[HttpClient]
  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val appConfig = app.injector.instanceOf[AppConfig]
  private lazy val connector = new TaxEnrolmentsConnector(appConfig, http, metrics)

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
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-EMAC-AddKnownFacts-HMRC-AS-AGENT-PUT")
    }

    "propagate an exception after failing to create known facts" in {
      createKnownFactsFails(arn.value)

      val exception = intercept[UpstreamErrorResponse] {
        await(connector.addKnownFacts(arn.value, knownFactKey, postcode))
      }
      exception.statusCode shouldBe 500
    }
  }

  "deleteKnownFacts" should {
    "return successfully deleting known facts" in {
      givenCleanMetricRegistry()
      deleteKnownFactsSucceeds(arn.value)
      val result = await(connector.deleteKnownFacts(arn))
      result shouldBe 204
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-EMAC-DeleteKnownFacts-HMRC-AS-AGENT-DELETE")
    }

    "propagate an exception after failing to delete known facts" in {
      deleteKnownFactsFails(arn.value)

      val exception = intercept[UpstreamErrorResponse] {
        await(connector.deleteKnownFacts(arn))
      }
      exception.statusCode shouldBe 500
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
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-EMAC-Enrol-HMRC-AS-AGENT-POST")
    }

    "propagate an exception for a failed enrolment" in {
      enrolmentFails(groupId, arn.value)

      val exception = intercept[UpstreamErrorResponse] {
        await(connector.enrol(groupId, arn, enrolmentRequest))
      }

      exception.statusCode shouldBe 500
    }
  }

  "hasPrincipalGroupIds" should {

    "return true if a successful query for principal enrolments returns some groups" in {
      givenCleanMetricRegistry()
      allocatedPrincipalEnrolmentExists(arn.value, groupId)
      val result = await(connector.hasPrincipalGroupIds(arn))
      result shouldBe true
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-EMAC-GetPrincipalGroupIdFor-HMRC-AS-AGENT-GET")
    }

    "return false after a successful query for principal enrolment" in {
      givenCleanMetricRegistry()
      allocatedPrincipalEnrolmentNotExists(arn.value)
      val result = await(connector.hasPrincipalGroupIds(arn))
      result shouldBe false
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-EMAC-GetPrincipalGroupIdFor-HMRC-AS-AGENT-GET")
    }

    "propagate an exception for a failed query" when {
      "failed with 500" in {
        allocatedPrincipalEnrolmentFails(arn.value, 500)

        val exception = intercept[UpstreamErrorResponse] {
          await(connector.hasPrincipalGroupIds(arn))
        }

        exception.statusCode shouldBe 500
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
