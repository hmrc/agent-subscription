/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentsubscription.controllers

import org.scalatest.concurrent.Eventually._
import play.api.libs.json._
import uk.gov.hmrc.agentsubscription.audit.AgentSubscriptionEvent.CheckAgencyStatus
import uk.gov.hmrc.agentsubscription.stubs.DataStreamStub.{writeAuditMergedSucceeds, writeAuditSucceeds}
import uk.gov.hmrc.agentsubscription.stubs.{AuthStub, DataStreamStub, DesStubs}
import uk.gov.hmrc.agentsubscription.support.{BaseAuditSpec, Resource}
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegments

import scala.language.postfixOps

class RegistrationAuditingSpec extends BaseAuditSpec with DesStubs with AuthStub {

  private val utr = "2000000000"
  private val postcode = "AA1 1AA"

  "GET of /registration/:utr/postcode/:postcode" should {
    "audit a CheckAgencyStatus event" in {
      writeAuditMergedSucceeds()
      writeAuditSucceeds()

      requestIsAuthenticated().andIsAnAgent()
      organisationRegistrationExists(utr, true)

      val path = encodePathSegments("agent-subscription", "registration", utr, "postcode", postcode)

      val response = await(new Resource(path, port).get)
      response.status shouldBe 200

      (response.json \ "isSubscribedToAgentServices").as[Boolean] shouldBe true
      (response.json \ "taxpayerName").as[String] shouldBe "My Agency"

      eventually {
        DataStreamStub.verifyAuditRequestSent(
          CheckAgencyStatus,
          expectedTags(path),
          expectedDetails(utr, postcode))
      }
    }
  }

  private def expectedDetails(utr: String, postcode: String): JsObject =
    Json.parse(
      s"""
         |{
         |  "utr": "$utr",
         |  "postcode": "$postcode",
         |  "knownFactsMatched": true,
         |  "isSubscribedToAgentServices": true,
         |  "agentReferenceNumber": "TARN0000001"
         |}
         |""".stripMargin)
      .asInstanceOf[JsObject]

  private def expectedTags(path: String): JsObject =
    Json.parse(
      s"""
         |{
         |  "path": "$path",
         |  "transactionName": "Check agency status"
         |}
         |""".stripMargin)
      .asInstanceOf[JsObject]
}
