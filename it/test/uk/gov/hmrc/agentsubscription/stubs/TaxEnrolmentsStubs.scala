/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock._

trait TaxEnrolmentsStubs {

  val createKnownFactsUrl = "/tax-enrolments/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~"
  val deleteKnownFactsUrl = "/enrolment-store-proxy/enrolment-store/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~"
  def enrolmentUrl(groupId: String, arn: String) =
    s"/tax-enrolments/groups/$groupId/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~$arn"
  def es1Url(arn: String) =
    s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~$arn/groups?type=principal"

  private val arnRegex = "[a-zA-Z]{1}ARN[0-9]{7}"

  def createKnownFactsSucceeds(arn: String): Unit = {
    stubFor(put(urlEqualTo(s"$createKnownFactsUrl$arn")).willReturn(aResponse().withStatus(200)))
    ()
  }

  def createKnownFactsFails(arn: String): Unit = {
    stubFor(put(urlEqualTo(s"$createKnownFactsUrl$arn")).willReturn(aResponse().withStatus(500)))
    ()
  }

  def verifyCreateKnownFactsCalled(times: Int): Unit = {
    val urlRegex = createKnownFactsUrl.replace("/", "\\/") + arnRegex

    verify(times, putRequestedFor(urlMatching(urlRegex)))
    ()
  }

  def deleteKnownFactsSucceeds(arn: String): Unit = {
    stubFor(delete(urlEqualTo(s"$deleteKnownFactsUrl$arn")).willReturn(aResponse().withStatus(204)))
    ()
  }

  def deleteKnownFactsFails(arn: String): Unit = {
    stubFor(delete(urlEqualTo(s"$deleteKnownFactsUrl$arn")).willReturn(aResponse().withStatus(500)))
    ()
  }

  def verifyDeleteKnownFactsCalled(times: Int): Unit = {
    val urlRegex = deleteKnownFactsUrl.replace("/", "\\/") + arnRegex

    verify(times, deleteRequestedFor(urlMatching(urlRegex)))
    ()
  }

  def enrolmentSucceeds(groupId: String, arn: String): Unit = {
    stubFor(post(urlEqualTo(enrolmentUrl(groupId, arn))).willReturn(aResponse().withStatus(200)))
    ()
  }

  def enrolmentFails(groupId: String, arn: String): Unit = {
    stubFor(post(urlEqualTo(enrolmentUrl(groupId, arn))).willReturn(aResponse().withStatus(500)))
    ()
  }

  def verifyEnrolmentCalled(times: Int): Unit = {
    val urlRegex = enrolmentUrl("SOMEGROUPID", "SOMEARN")
      .replace("/", "\\/")
      .replace("SOMEGROUPID", ".+")
      .replace("SOMEARN", arnRegex)

    verify(times, postRequestedFor(urlMatching(urlRegex)))
    ()
  }

  def allocatedPrincipalEnrolmentExists(arn: String, groupId: String): Unit = {
    stubFor(
      get(urlEqualTo(es1Url(arn)))
        .willReturn(
          aResponse()
            .withBody(s"""
                         |{
                         |    "principalGroupIds": [ "$groupId" ],
                         |    "delegatedGroupIds": []
                         |}
          """.stripMargin)
            .withStatus(200)
        )
    )
    ()
  }

  def allocatedPrincipalEnrolmentNotExists(arn: String): Unit = {
    stubFor(
      get(urlEqualTo(es1Url(arn)))
        .willReturn(
          aResponse()
            .withStatus(204)
        )
    )
    ()
  }

  def allocatedPrincipalEnrolmentFails(arn: String, errorCode: Int = 500): Unit = {
    stubFor(get(urlEqualTo(es1Url(arn))).willReturn(aResponse().withStatus(errorCode)))
    ()
  }

  def verifyAllocatedPrincipalEnrolmentCalled(times: Int) = {
    val urlRegex = es1Url("SOMEARN")
      .replace("/", "\\/")
      .replace("?", "\\?")
      .replace("SOMEARN", arnRegex)

    verify(times, getRequestedFor(urlMatching(urlRegex)))
    ()
  }
}
