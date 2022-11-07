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
import com.mongodb.client.model.ReplaceOptions
import org.mongodb.scala.model.Filters.{equal, or}
import org.mongodb.scala.model.Indexes._
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.AuthProviderId
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SubscriptionJourneyRepositoryImpl])
trait SubscriptionJourneyRepository {

  def upsert(authProviderId: AuthProviderId, record: SubscriptionJourneyRecord): Future[Option[UpsertType]]
  def updateOnUtr(utr: Utr, record: SubscriptionJourneyRecord): Future[Option[Long]]
  def findByAuthId(authProviderId: AuthProviderId): Future[Option[SubscriptionJourneyRecord]]
  def findByContinueId(continueId: String): Future[Option[SubscriptionJourneyRecord]]
  def findByUtr(utr: Utr): Future[Option[SubscriptionJourneyRecord]]
  def delete(utr: Utr): Future[Option[Long]]
}

@Singleton
class SubscriptionJourneyRepositoryImpl @Inject() (mongo: MongoComponent)(implicit
  ec: ExecutionContext,
  appConfig: AppConfig
) extends PlayMongoRepository[SubscriptionJourneyRecord](
      mongoComponent = mongo,
      collectionName = "subscription-journey",
      domainFormat = SubscriptionJourneyRecord.subscriptionJourneyFormat,
      indexes = Seq(
        IndexModel(
          ascending("authProviderId"),
          IndexOptions().unique(true).name("primaryAuthId")
        ),
        IndexModel(
          ascending("userMappings.authProviderId"),
          IndexOptions().unique(true).sparse(true).name("mappedAuthId")
        ),
        IndexModel(
          ascending("cleanCredsAuthProviderId"),
          IndexOptions().unique(true).sparse(true).name("cleanCredsAuthProviderId")
        ),
        IndexModel(
          ascending("businessDetails.utr"),
          IndexOptions().unique(true).name("utr")
        ),
        IndexModel(
          ascending("continueId"),
          IndexOptions().unique(true).sparse(true).name("continueId")
        ),
        IndexModel(
          ascending("lastModifiedDate"),
          IndexOptions().unique(false).name("lastModifiedDateTtl")
        ),
        IndexModel(
          ascending("lastModifiedDate"),
          IndexOptions()
            .unique(false)
            .name("lastModifiedDateTtl")
            .expireAfter(appConfig.mongodbSubscriptionJourneyTTL, TimeUnit.SECONDS)
        )
      )
    ) with SubscriptionJourneyRepository {

  private def replaceOptions(upsert: Boolean) = new ReplaceOptions().upsert(upsert)

  def upsert(authProviderId: AuthProviderId, record: SubscriptionJourneyRecord): Future[Option[UpsertType]] =
    collection
      .replaceOne(
        equal("authProviderId", authProviderId.id),
        record,
        replaceOptions(true)
      )
      .headOption()
      .map(
        _.map(result =>
          result.getModifiedCount match {
            case 0L => RecordInserted(result.getUpsertedId.asObjectId().getValue.toString)
            case 1L => RecordUpdated
            case x  => throw new RuntimeException(s"Update modified count should not have been $x")
          }
        )
      )

  def updateOnUtr(utr: Utr, record: SubscriptionJourneyRecord): Future[Option[Long]] =
    collection
      .replaceOne(
        equal("businessDetails.utr", utr.value),
        record
      )
      .toFutureOption()
      .map(_.map(_.getModifiedCount))

  def findByAuthId(
    authProviderId: AuthProviderId
  ): Future[Option[SubscriptionJourneyRecord]] =
    collection
      .find(
        or(
          equal("authProviderId", authProviderId),
          equal("cleanCredsAuthProviderId", authProviderId),
          equal("userMappings.authProviderId", authProviderId)
        )
      )
      .headOption()

  def findByContinueId(continueId: String): Future[Option[SubscriptionJourneyRecord]] =
    collection
      .find(equal("continueId", continueId))
      .headOption()

  def findByUtr(utr: Utr): Future[Option[SubscriptionJourneyRecord]] =
    collection
      .find(equal("businessDetails.utr", utr.value))
      .headOption()

  def delete(utr: Utr): Future[Option[Long]] =
    collection
      .deleteOne(equal("businessDetails.utr", utr.value))
      .toFutureOption()
      .map(_.map(_.getDeletedCount))
}
