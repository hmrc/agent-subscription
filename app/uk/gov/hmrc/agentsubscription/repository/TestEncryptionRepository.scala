/*
 * Copyright 2023 HM Revenue & Customs
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

import com.google.inject.ImplementedBy
import com.mongodb.MongoWriteException
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions}
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TestEncryptionRepositoryImpl])
trait TestEncryptionRepository {
  def create(
    testData: TestData
  ): Future[Option[Boolean]]

  def update(
    testData: TestData
  ): Future[Option[Boolean]]

  def listTestData: Future[Seq[TestData]]
  def findTestData(arn: String): Future[Option[TestData]]
}

@Singleton
class TestEncryptionRepositoryImpl @Inject() (mongo: MongoComponent, @Named("aes") crypto: Encrypter with Decrypter)(
  implicit ec: ExecutionContext
) extends PlayMongoRepository[TestData](
      mongoComponent = mongo,
      collectionName = "test-encryption",
      domainFormat = TestData.testDataFormat,
      replaceIndexes = true,
      indexes = Seq(
        IndexModel(ascending("arn"), IndexOptions().name("Arn").unique(true))
      )
    ) with TestEncryptionRepository with Logging {

  implicit val theCrypto: Encrypter with Decrypter = crypto

  def create(
    testData: TestData
  ): Future[Option[Boolean]] =
    collection
      .insertOne(testData.copy(encrypted = Some(true), message = crypto.encrypt(PlainText(testData.message)).value))
      .headOption()
      .map(_.map(result => result.wasAcknowledged()))
      .recoverWith { case e: MongoWriteException =>
        logger.error(s"Failed to create test record for ${testData.arn}", e)
        Future.successful(None)
      }

  def update(
    testData: TestData
  ): Future[Option[Boolean]] =
    collection
      .replaceOne(
        equal("arn", testData.arn),
        testData.copy(encrypted = Some(true), message = crypto.encrypt(PlainText(testData.message)).value)
      )
      .headOption()
      .map(_.map(result => result.wasAcknowledged()))
      .recoverWith { case e: MongoWriteException =>
        logger.error(s"Failed to create test record for ${testData.arn}", e)
        Future.successful(None)
      }

  private def maybeDecrypt(testData: TestData): TestData =
    if (testData.encrypted.contains(true)) {
      testData.copy(message = crypto.decrypt(Crypted(testData.message)).value)
    } else testData

  def listTestData: Future[Seq[TestData]] =
    collection
      .find(Filters.empty())
      .collect()
      .toFuture()
      .map(_.map(maybeDecrypt))

  def findTestData(arn: String): Future[Option[TestData]] =
    collection
      .find(Filters.equal("arn", arn))
      .headOption()
      .map(_.map(maybeDecrypt))

}

case class TestData(
  arn: String,
  message: String,
  encrypted: Option[Boolean]
)

object TestData {

  implicit val testDataFormat: OFormat[TestData] = Json.format[TestData]
}
