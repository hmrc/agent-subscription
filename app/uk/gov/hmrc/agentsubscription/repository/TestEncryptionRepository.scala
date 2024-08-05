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
import com.mongodb.client.model.Collation
import org.mongodb.scala.model.CollationStrength.SECONDARY
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions}
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.agentsubscription.repository.TestEncryptionRepositoryImpl.{caseInsensitiveCollation, sensitiveStringFormat}
import uk.gov.hmrc.agentsubscription.repository.models.SensitiveTestData
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TestEncryptionRepositoryImpl])
trait TestEncryptionRepository {
  def create(
    arn: String,
    message: String
  ): Future[Option[Boolean]]

  def listTestData: Future[Seq[TestData]]
  def findTestData(arn: String): Future[Option[TestData]]
}

@Singleton
class TestEncryptionRepositoryImpl @Inject() (mongo: MongoComponent, @Named("aes") crypto: Encrypter with Decrypter)(
  implicit ec: ExecutionContext
) extends PlayMongoRepository[SensitiveTestData](
      mongoComponent = mongo,
      collectionName = "test-encryption",
      domainFormat = SensitiveTestData.format(crypto),
      replaceIndexes = true,
      extraCodecs = Seq(
        // Sensitive string codec so we can operate on individual string fields
        Codecs.playFormatCodec(sensitiveStringFormat(crypto))
      ),
      indexes = Seq(
        IndexModel(ascending("arn"), IndexOptions().name("Arn").unique(true))
      )
    ) with TestEncryptionRepository with Logging {

  // Ensure that we are using a deterministic cryptographic algorithm, or we won't be able to search on encrypted fields
  require(
    crypto.encrypt(PlainText("foo")) == crypto.encrypt(PlainText("foo")),
    s"Crypto algorithm provided is not deterministic."
  )

  implicit val theCrypto: Encrypter with Decrypter = crypto

  def create(
    arn: String,
    message: String
  ): Future[Option[Boolean]] =
    collection
      .insertOne(SensitiveTestData(TestData(arn, message)))
      .headOption()
      .map(_.map(result => result.wasAcknowledged()))
      .recoverWith { case e: MongoWriteException =>
        logger.error(s"Failed to create test record for $arn", e)
        Future.successful(None)
      }

  def listTestData: Future[Seq[TestData]] =
    collection
      .find(Filters.empty())
      .collation(caseInsensitiveCollation)
      .map(_.decryptedValue)
      .collect()
      .toFuture()

  def findTestData(arn: String): Future[Option[TestData]] =
    collection
      .find(Filters.equal("arn", arn))
      .collation(caseInsensitiveCollation)
      .map(a =>
        try
          a.decryptedValue
        catch {
          case _: Exception => TestData(arn, a.message.toString())
        }
      )
      .headOption()

}

object TestEncryptionRepositoryImpl {

  private def caseInsensitiveCollation: Collation =
    Collation.builder().locale("en").collationStrength(SECONDARY).build()
  private def sensitiveStringFormat(implicit crypto: Encrypter with Decrypter): Format[SensitiveString] =
    JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)
}

case class TestData(
  arn: String,
  message: String
)

object TestData {
  implicit val testDataFormat: OFormat[TestData] = Json.format[TestData]
}
