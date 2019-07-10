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

import javax.inject.{ Inject, Named, Singleton }
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{ Index, IndexType }
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.agentsubscription.model.subscriptionJourneyRepositoryModel.SubscriptionJourneyRecord
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SubscriptionJourneyRepository @Inject() (
  @Named("mongodb.subscriptionjourney.ttl") ttl: Int,
  mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[SubscriptionJourneyRecord, BSONObjectID](
    "subscription-journey",
    mongoComponent.mongoConnector.db,
    SubscriptionJourneyRecord.format,
    ReactiveMongoFormats.objectIdFormats) {

  override def indexes: Seq[Index] =
    Seq(
      Index(key = Seq("internalId" -> IndexType.Ascending), name = Some("internalIdUnique"), unique = true),
      Index(
        key = Seq("updatedDateTime" -> IndexType.Ascending),
        name = Some("updatedDateTime"),
        unique = false,
        options = BSONDocument("expireAfterSeconds" -> ttl)))

  def find(internalId: String)(implicit ec: ExecutionContext): Future[Option[SubscriptionJourneyRecord]] =
    super.find("internalId" -> internalId).map(_.headOption).map {
      case Some(record) => Some(record)
      case None => //check if internalId is anywhere in the mapping details bit
       ???
    }

  def create(subscriptionJourney: SubscriptionJourneyRecord)(implicit ec: ExecutionContext): Future[Unit] =
    insert(subscriptionJourney).map(_ => ())

  def update(internalId: String, subscriptionJourney: SubscriptionJourneyRecord)(implicit executionContext: ExecutionContext) =
    collection.findAndUpdate(Json.obj("internalId" -> internalId), subscriptionJourney)

  def delete(internalId: String)(implicit ec: ExecutionContext): Future[Unit] =
    remove("internalId" -> internalId).map(_ => ())
}
