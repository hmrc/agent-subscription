package uk.gov.hmrc.agentsubscription.connectors

import java.time.LocalDate

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.{ CompaniesHouseOfficer, Crn }
import uk.gov.hmrc.agentsubscription.stubs.CompaniesHouseStub
import uk.gov.hmrc.agentsubscription.support.{ BaseISpec, MetricsTestSupport }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global

class CompaniesHouseApiProxyConnectorISpec extends BaseISpec with CompaniesHouseStub with MetricsTestSupport with MockitoSugar {
  private implicit val hc = HeaderCarrier()

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val http: HttpClient = app.injector.instanceOf[HttpClient]
  private lazy val appConfig = app.injector.instanceOf[AppConfig]

  private lazy val connector: CompaniesHouseApiProxyConnector =
    new CompaniesHouseApiProxyConnectorImpl(appConfig, http, metrics)

  val crn = Crn("01234567")

  "GET companyOfficers" should {
    "return a list of officers for a valid CRN" in {

      givenCompaniesHouseOfficersListFoundForCrn(crn)
      val result = await(connector.getCompanyOfficers(crn))
      val date = LocalDate.parse("2019-12-27")

      result shouldBe List(CompaniesHouseOfficer("FERGUSON, Jim", Some(date)), CompaniesHouseOfficer("LUCAS, George", None))
    }

    "return Seq.empty when Unauthorized" in {

      givenCompaniesHouseOfficersListWithStatus(crn.value, 401)
      val result = await(connector.getCompanyOfficers(crn))

      result shouldBe Seq.empty
    }

  }
}
