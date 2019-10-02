/*
 * Copyright 2019 HM Revenue & Customs
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

import java.net.URL

import javax.inject.{ Inject, Named, Singleton }
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{ Index, IndexType }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.auth.AuthActions.AuthIds
import uk.gov.hmrc.agentsubscription.model.SubscriptionRequest
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class RecoveryRepository @Inject() (
  mongoComponent: ReactiveMongoComponent,
  @Named("indexes.background-build") indexInBackground: Boolean)
  extends ReactiveRepository[RecoveryData, BSONObjectID](
    "agent-recovery-store",
    mongoComponent.mongoConnector.db,
    RecoveryData.recoveryDataFormat,
    ReactiveMongoFormats.objectIdFormats) with StrictlyEnsureIndexes[RecoveryData, BSONObjectID] {

  Logger.logger.warn(s"Index in Background set to: $indexInBackground")

  override def indexes: Seq[Index] =
    Seq(
      Index(
        key = Seq("arn" -> IndexType.Ascending),
        name = Some("Arn"),
        unique = false,
        background = indexInBackground),
      Index(
        key = Seq("createdDate" -> IndexType.Ascending),
        name = Some("createDate"),
        unique = false,
        background = indexInBackground))

  def create(authIds: AuthIds, arn: Arn, subscriptionRequest: SubscriptionRequest, errorMessage: String)(implicit ec: ExecutionContext): Future[Unit] = {
    insert(RecoveryData(authIds, arn, subscriptionRequest, errorMessage)).map(_ => ())
      .recoverWith {
        case e =>
          Logger.error(s"Failed to create recovery record for ${arn.value}", e)
          Future failed e
      }
  }
}

case class RecoveryData(
  authIds: AuthIds,
  arn: Arn,
  subscriptionRequest: SubscriptionRequest,
  errorMessage: String,
  createdDate: DateTime = DateTime.now)

object RecoveryData {
  implicit val dateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val recoveryDataFormat: OFormat[RecoveryData] = Json.format[RecoveryData]
}