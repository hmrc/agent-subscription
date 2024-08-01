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
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.repository.TestEncryptionRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
@Singleton
class TestOnlyController @Inject() (
  testEncryptionRepository: TestEncryptionRepository
)(implicit val ec: ExecutionContext, cc: ControllerComponents, appConfig: AppConfig)
    extends BackendController(cc) with Logging {

  def create(arn: String): Action[AnyContent] = Action.async { _ =>
    for {
      a <- testEncryptionRepository.create(arn, "test")
    } yield Ok(
      s"create test data for arn: $arn with result: ${a.toString} [fieldLevelEncryption.key: ${appConfig.cryptoKey}]"
    )
  }

  def listTestData: Action[AnyContent] = Action.async { _ =>
    for {
      a <- testEncryptionRepository.listTestData
    } yield Ok(s"""${a.map(r => s"${r.arn} - ${r.message}").mkString("\n")}""")
  }

}
