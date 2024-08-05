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

package uk.gov.hmrc.agentsubscription.repository.models

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.agentsubscription.repository.TestData
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption

case class SensitiveTestData(arn: String, message: SensitiveString) extends Sensitive[TestData] {

  override def decryptedValue: TestData =
    TestData(
      arn = arn,
      message =
        try message.decryptedValue
        catch { case e: Exception => message.toString() }
    )

}

object SensitiveTestData {
  def apply(testData: TestData): SensitiveTestData = SensitiveTestData(
    arn = testData.arn,
    message = SensitiveString(testData.message)
  )
  implicit def format(implicit crypto: Encrypter with Decrypter): OFormat[SensitiveTestData] = {
    implicit val sensitiveStringFormat: Format[SensitiveString] =
      JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
    Json.format[SensitiveTestData]
  }
}
