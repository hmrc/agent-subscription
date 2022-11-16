/*
 * Copyright 2022 HM Revenue & Customs
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
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.auth.AuthActions.AuthIds
import uk.gov.hmrc.agentsubscription.model.SubscriptionRequest
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[RecoveryRepositoryImpl])
trait RecoveryRepository {
  def create(
    authIds: AuthIds,
    arn: Arn,
    subscriptionRequest: SubscriptionRequest,
    errorMessage: String
  ): Future[Option[Boolean]]
}

@Singleton
class RecoveryRepositoryImpl @Inject() (mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[RecoveryData](
      mongoComponent = mongo,
      collectionName = "agent-recovery-store",
      domainFormat = RecoveryData.recoveryDataFormat,
      replaceIndexes = true,
      extraCodecs = Seq.empty,
      indexes = Seq(
        IndexModel(ascending("arn"), IndexOptions().name("Arn").unique(false)),
        IndexModel(ascending("createdDate"), IndexOptions().name("createDate").unique(false))
      )
    ) with RecoveryRepository with Logging {

  def create(
    authIds: AuthIds,
    arn: Arn,
    subscriptionRequest: SubscriptionRequest,
    errorMessage: String
  ): Future[Option[Boolean]] =
    collection
      .insertOne(RecoveryData(authIds, arn, subscriptionRequest, errorMessage))
      .headOption()
      .map(_.map(result => result.wasAcknowledged()))
      .recoverWith { case e: MongoWriteException =>
        logger.error(s"Failed to create recovery record for ${arn.value}", e)
        Future.successful(None)
      }
}

case class RecoveryData(
  authIds: AuthIds,
  arn: Arn,
  subscriptionRequest: SubscriptionRequest,
  errorMessage: String,
  createdDate: LocalDateTime = LocalDateTime.now
)

object RecoveryData {
  implicit val recoveryDataFormat: OFormat[RecoveryData] = Json.format[RecoveryData]
}
