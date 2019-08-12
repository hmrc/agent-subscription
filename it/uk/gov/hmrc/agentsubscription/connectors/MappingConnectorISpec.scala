package uk.gov.hmrc.agentsubscription.connectors

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.stubs.MappingStubs
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class MappingConnectorISpec extends BaseISpec with MappingStubs {

  val connector = app.injector.instanceOf[MappingConnector]

  val arn = Arn("TARN0000001")

  private implicit val hc = HeaderCarrier()

  "createMappings" should {
    "return the status code created when mapping is successfully created" in {
      givenMappingCreationWithStatus(arn, 201)

      val result = await(connector.createMappings(arn))

      result shouldBe 201
    }

    "return status code conflict when the mappings are already mapped" in {
      givenMappingCreationWithStatus(arn, 409)

      val result = await(connector.createMappings(arn))

      result shouldBe 409
    }

    "return status code forbidden when the user is not allowed to map" in {
      givenMappingCreationWithStatus(arn, 403)

      val result = await(connector.createMappings(arn))

      result shouldBe 403
    }
  }

}
