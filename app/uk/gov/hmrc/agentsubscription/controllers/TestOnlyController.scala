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

package uk.gov.hmrc.agentsubscription.controllers

import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.agentsubscription.model.TestBusinessAddress
import uk.gov.hmrc.agentsubscription.repository.{TestData, TestEncryptionRepository}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
@Singleton
class TestOnlyController @Inject() (
  testEncryptionRepository: TestEncryptionRepository
)(implicit val ec: ExecutionContext, cc: ControllerComponents)
    extends BackendController(cc) with Logging {

  val fakeBusinessAddress: TestBusinessAddress = TestBusinessAddress(
    "1 Some Street",
    Some("Some Town"),
    None,
    None,
    Some("BN1 1XX"),
    "GB",
    Some(true)
  )

  def create(arn: String): Action[AnyContent] = Action.async { _ =>
    val testData = TestData(arn, "test", fakeBusinessAddress, encrypted = Some(true))
    for {
      a <- testEncryptionRepository.create(testData)
    } yield Ok(
      s"create test data for arn: $arn with result: ${a.toString}"
    )
  }

  def listTestData: Action[AnyContent] = Action.async { _ =>
    for {
      a <- testEncryptionRepository.listTestData
    } yield Ok(
      s"""${a
          .map(r =>
            s"${r.arn} - " +
              s"${r.message} - " +
              s"${r.businessAddress.addressLine1}" +
              s"${r.businessAddress.addressLine2.map(f => s", $f")}" +
              s"${r.businessAddress.addressLine3.map(f => s", $f")}" +
              s"${r.businessAddress.addressLine4.map(f => s", $f")}" +
              s"${r.businessAddress.postalCode.map(f => s", $f")}" +
              s"${r.businessAddress.countryCode} | encryption = ${r.encrypted.contains(true)}"
          )
          .mkString("\n")}"""
    )
  }

  def updateTestData(arn: String): Action[AnyContent] = Action.async { _ =>
    val testData = TestData(arn, "updated", fakeBusinessAddress, encrypted = Some(false))
    for {
      a <- testEncryptionRepository.update(testData)
    } yield Ok(
      s"update test data for arn: $arn with result: ${a.toString}"
    )
  }

  def findTestData(arn: String): Action[AnyContent] = Action.async { _ =>
    for {
      a <- testEncryptionRepository.findTestData(arn)
    } yield Ok(
      a.fold(s"no data found for arn: $arn")(r => s"""${r.arn} - " +
          s"${r.message} - " +
          s"${r.businessAddress.addressLine1}" +
          s"${r.businessAddress.addressLine2.map(f => s", $f")}" +
          s"${r.businessAddress.addressLine3.map(f => s", $f")}" +
          s"${r.businessAddress.addressLine4.map(f => s", $f")}" +
          s"${r.businessAddress.postalCode.map(f => s", $f")}" +
          s"${r.businessAddress.countryCode} | encryption = ${r.encrypted.contains(true)}""")
    )
  }

}
