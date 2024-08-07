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
import uk.gov.hmrc.agentsubscription.connectors.BusinessAddress
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter}
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
      domainFormat = TestData.format(crypto),
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
      .insertOne(testData.copy(encrypted = Some(true)))
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
        testData.copy(encrypted = Some(true))
      )
      .headOption()
      .map(_.map(result => result.wasAcknowledged()))
      .recoverWith { case e: MongoWriteException =>
        logger.error(s"Failed to create test record for ${testData.arn}", e)
        Future.successful(None)
      }

  def listTestData: Future[Seq[TestData]] =
    collection
      .find(Filters.empty())
      .collect()
      .toFuture()

  def findTestData(arn: String): Future[Option[TestData]] =
    collection
      .find(Filters.equal("arn", arn))
      .headOption()

}

case class TestData(
  arn: String,
  message: String,
  businessAddress: BusinessAddress,
  encrypted: Option[Boolean]
)

object TestData {
  def format(implicit crypto: Encrypter with Decrypter): Format[TestData] = {

    def reads(json: JsValue): JsResult[TestData] =
      for {
        isEncrypted <- (json \ "encrypted").validateOpt[Boolean]
        testData <- isEncrypted match {
                      case Some(true) =>
                        for {
                          arn <- (json \ "arn").validate[String]
                          message = (json \ "message").validate[String] match {
                                      case JsSuccess(value, _) => crypto.decrypt(Crypted(value)).value
                                      case _ => throw new RuntimeException("Failed to decrypt message")
                                    }
                          businessAddress = (json \ "businessAddress").validate[BusinessAddress] match {
                                              case JsSuccess(value, _) =>
                                                BusinessAddress(
                                                  addressLine1 = crypto.decrypt(Crypted(value.addressLine1)).value,
                                                  addressLine2 = value.addressLine2.map { f: String =>
                                                    crypto.decrypt(Crypted(f)).value
                                                  },
                                                  addressLine3 = value.addressLine3.map { f: String =>
                                                    crypto.decrypt(Crypted(f)).value
                                                  },
                                                  addressLine4 = value.addressLine4.map { f: String =>
                                                    crypto.decrypt(Crypted(f)).value
                                                  },
                                                  postalCode = value.postalCode.map { f: String =>
                                                    crypto.decrypt(Crypted(f)).value
                                                  },
                                                  countryCode = crypto.decrypt(Crypted(value.countryCode)).value
                                                )
                                            }
                        } yield TestData(
                          arn = arn,
                          message = message,
                          encrypted = Some(true),
                          businessAddress = businessAddress
                        )
                      case _ =>
                        for {
                          arn             <- (json \ "arn").validate[String]
                          message         <- (json \ "message").validate[String]
                          businessAddress <- (json \ "businessAddress").validate[BusinessAddress]
                        } yield TestData(
                          arn = arn,
                          message = message,
                          encrypted = Some(false),
                          businessAddress = businessAddress
                        )
                    }
      } yield testData

    def writes(testData: TestData): JsValue =
      Json.obj(
        "arn"             -> testData.arn,
        "message"         -> stringEncrypter.writes(testData.message),
        "encrypted"       -> testData.encrypted,
        "businessAddress" -> testData.businessAddress
      )

    Format(reads(_), testData => writes(testData))
  }
}
