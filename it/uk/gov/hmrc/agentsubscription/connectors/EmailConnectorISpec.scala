package uk.gov.hmrc.agentsubscription.connectors

import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, MtdItId, Vrn }
import uk.gov.hmrc.agentsubscription.model.EmailInformation
import uk.gov.hmrc.agentsubscription.stubs.EmailStub
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
class EmailConnectorISpec extends BaseISpec with EmailStub {

  val connector = app.injector.instanceOf[EmailConnector]

  val arn = Arn("TARN0000001")
  val nino = Nino("AB123456A")
  val mtdItId = MtdItId("LC762757D")
  val vrn = Vrn("101747641")

  private implicit val hc = HeaderCarrier()

  "sendEmail" should {
    val emailInfo = EmailInformation(Seq("abc@xyz.com"), "template-id", Map("param1" -> "foo", "param2" -> "bar"))

    "return Unit when the email service responds" in {
      givenEmailSent(emailInfo)

      val result = await(connector.sendEmail(emailInfo))

      result shouldBe ()
    }
    "throw an Exception when the email service throws an Exception" in {
      givenEmailReturns500

      intercept[Exception] {
        await(connector.sendEmail(emailInfo))
      }.getMessage should startWith("sending email failed")
    }
  }
}
