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

package uk.gov.hmrc.agentsubscription.repository

import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.auth.AuthActions.AuthIds
import uk.gov.hmrc.agentsubscription.model.SubscriptionRequest
import uk.gov.hmrc.agentsubscription.support.UnitSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global

class RecoveryDataRepositoryISpec extends UnitSpec with DefaultPlayMongoRepositorySupport[RecoveryData] {

  override lazy val repository = new RecoveryRepositoryImpl(mongoComponent)

  override def checkTtlIndex = false // temporary until we review the RecoveryData collection

  private val utr = Utr("7000000002")

  private val subscriptionRequest: String =
    s"""
       |{
       |  "utr": "${utr.value}",
       |  "knownFacts": {
       |    "postcode": "AA1 1AA"
       |  },
       |  "agency": {
       |    "name": "My Agency",
       |    "address": {
       |      "addressLine1": "Flat 1",
       |      "addressLine2": "1 Some Street",
       |      "addressLine3": "Anytown",
       |      "addressLine4": "County",
       |      "postcode": "AA1 2AA",
       |      "countryCode": "GB"
       |    },
       |    "email": "agency@example.com",
       |    "telephone": "0123 456 7890"
       |  }
       |}
     """.stripMargin

  val arn = Arn("TARN0000001")
  val authIds = AuthIds("userId", "groupId")
  val subscriptionRequestBody: SubscriptionRequest = Json.parse(subscriptionRequest).as[SubscriptionRequest]

  "RecoveryDataRepository" should {
    "create a record if Upsert KnownFacts failed" in {
      val result = await(repository.create(authIds, arn, subscriptionRequestBody, "Failed to Upsert Known Facts"))
      result shouldBe Some(true)
    }

    "create a record if Allocate Enrolment failed" in {
      val result = await(repository.create(authIds, arn, subscriptionRequestBody, "Failed to Enrol to HMRC-AS-AGENT"))
      result shouldBe Some(true)
    }
  }
}
