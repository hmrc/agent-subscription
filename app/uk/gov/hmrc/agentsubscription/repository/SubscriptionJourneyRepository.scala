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
import uk.gov.hmrc.agentsubscription.model.AuthProviderId
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

  def upsert(authProviderId: AuthProviderId, record: SubscriptionJourneyRecord)(implicit ec: ExecutionContext): Future[Unit] = {
    collection.update(ordered = false).one(
      Json.obj("authProviderId" -> authProviderId.id),
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
      Index(key = Seq("authProviderId" -> IndexType.Ascending), name = Some("primaryAuthId"), unique = true),
      Index(key = Seq("userMappings.authProviderId" -> IndexType.Ascending), name = Some("mappedAuthId"), unique = true, sparse = true),
      Index(key = Seq("cleanCredsAuthProviderId" -> IndexType.Ascending), name = Some("cleanCredsAuthProviderId"), unique = true, sparse = true),
      Index(key = Seq("businessDetails.utr" -> IndexType.Ascending), name = Some("utr"), unique = true),
      Index(key = Seq("continueId" -> IndexType.Ascending), name = Some("continueId"), unique = true, sparse = true),
      Index(
        key = Seq("lastModifiedDate" -> IndexType.Ascending),
        name = Some("lastModifiedDateTtl"),
        unique = false,
        options = BSONDocument("expireAfterSeconds" -> expireRecordAfterSeconds)))

  def findByAuthId(authProviderId: AuthProviderId)(implicit ec: ExecutionContext): Future[Option[SubscriptionJourneyRecord]] =
    super.find(
      query = "$or" -> Json.arr(
        Json.obj(fields = "authProviderId" -> authProviderId),
        Json.obj(fields = "cleanCredsAuthProviderId" -> authProviderId),
        Json.obj(fields = "userMappings.authProviderId" -> authProviderId))).map(_.headOption)

  def findByContinueId(continueId: String)(implicit ec: ExecutionContext): Future[Option[SubscriptionJourneyRecord]] =
    super.find("continueId" -> continueId).map(_.headOption)

  def findByUtr(utr: Utr)(implicit ec: ExecutionContext): Future[Option[SubscriptionJourneyRecord]] =
    super.find("businessDetails.utr" -> utr.value).map(_.headOption)

  def delete(utr: Utr)(implicit ec: ExecutionContext): Future[Unit] =
    remove("businessDetails.utr" -> utr.value).map(_ => ())
}
