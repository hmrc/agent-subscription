package uk.gov.hmrc.agentsubscription.connectors

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.{CompaniesHouseDateOfBirth, CompaniesHouseOfficer, Crn}
import uk.gov.hmrc.agentsubscription.stubs.CompaniesHouseStub
import uk.gov.hmrc.agentsubscription.support.{BaseISpec, MetricsTestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global

class CompaniesHouseApiProxyConnectorISpec
    extends BaseISpec with CompaniesHouseStub with MetricsTestSupport with MockitoSugar {
  private implicit val hc = HeaderCarrier()

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val http: HttpClient = app.injector.instanceOf[HttpClient]
  private lazy val appConfig = app.injector.instanceOf[AppConfig]

  private lazy val connector: CompaniesHouseApiProxyConnector =
    new CompaniesHouseApiProxyConnectorImpl(appConfig, http, metrics)

  val crn = Crn("01234567")

  "GET companyOfficers" should {
    "return a list of officers who's surname matches the nameToMatch for a given CRN" in {

      givenSuccessfulCompaniesHouseResponseMultipleMatches(crn, "FERGUSON")
      val result = await(connector.getCompanyOfficers(crn, "FERGUSON"))

      result shouldBe List(
        CompaniesHouseOfficer("FERGUSON, David", Some(CompaniesHouseDateOfBirth(Some(4), 8, 1967))),
        CompaniesHouseOfficer("FERGUSON, Hamish", Some(CompaniesHouseDateOfBirth(None, 4, 1974))),
        CompaniesHouseOfficer("FERGUSON, Iain Blair", Some(CompaniesHouseDateOfBirth(None, 2, 1973))),
        CompaniesHouseOfficer("FERGUSON, Mark Richard", Some(CompaniesHouseDateOfBirth(None, 10, 1972)))
      )
    }

    "return Seq.empty when Unauthorized" in {

      givenCompaniesHouseOfficersListWithStatus(crn.value, "FERGUSON", 401)
      val result = await(connector.getCompanyOfficers(crn, "FERGUSON"))

      result shouldBe Seq.empty
    }

  }
}
