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

import javax.inject.{ Inject, Singleton }
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{ Index, IndexType }
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.InternalId
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SubscriptionJourneyRepository @Inject() (
  mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[SubscriptionJourneyRecord, BSONObjectID](
    "subscription-journey",
    mongoComponent.mongoConnector.db,
    SubscriptionJourneyRecord.subscriptionJourneyFormat,
    ReactiveMongoFormats.objectIdFormats) {

  private def expireRecordAfterSeconds: Long = 2592000 // 30 days

  def upsert(id: InternalId, record: SubscriptionJourneyRecord)(implicit ec: ExecutionContext): Future[Unit] = {
    collection.update(ordered = false).one(
      Json.obj("internalId" -> id.id),
      record,
      upsert = true).checkResult
  }

  private implicit class WriteResultChecker(future: Future[WriteResult]) {
    def checkResult(implicit ec: ExecutionContext): Future[Unit] = future.map { writeResult =>
      if (hasProblems(writeResult)) throw new RuntimeException(writeResult.toString)
      else ()
    }
  }

  private def hasProblems(writeResult: WriteResult): Boolean =
    !writeResult.ok || writeResult.writeErrors.nonEmpty || writeResult.writeConcernError.isDefined

  override def indexes: Seq[Index] =
    Seq(
      Index(key = Seq("internalId" -> IndexType.Ascending), name = Some("primaryInternalId"), unique = true),
      Index(key = Seq("userMappings.internalId" -> IndexType.Ascending), name = Some("mappedInternalId"), unique = true),
      Index(key = Seq("businessDetails.utr" -> IndexType.Ascending), name = Some("utr"), unique = true),
      Index(key = Seq("continueId" -> IndexType.Ascending), name = Some("continueId"), unique = true),
      Index(
        key = Seq("lastModifiedDate" -> IndexType.Ascending),
        name = Some("lastModifiedDateTtl"),
        unique = false,
        options = BSONDocument("expireAfterSeconds" -> expireRecordAfterSeconds)))

  def findByPrimaryId(internalId: InternalId)(implicit ec: ExecutionContext): Future[Option[SubscriptionJourneyRecord]] =
    super.find("internalId" -> internalId).map(_.headOption)

  def findByMappedId(internalId: InternalId)(implicit ec: ExecutionContext): Future[Option[SubscriptionJourneyRecord]] =
    super.find("userMappings.internalId" -> internalId).map(_.headOption)

  def findByContinueId(continueId: String)(implicit ec: ExecutionContext): Future[Option[SubscriptionJourneyRecord]] =
    super.find("continueId" -> continueId).map(_.headOption)

  def findByUtr(utr: Utr)(implicit ec: ExecutionContext): Future[Option[SubscriptionJourneyRecord]] =
    super.find("businessDetails.utr" -> utr.value).map(_.headOption)

  def delete(primaryInternalId: InternalId)(implicit ec: ExecutionContext): Future[Unit] =
    remove("internalId" -> primaryInternalId.id).map(_ => ())
}
