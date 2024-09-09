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

package uk.gov.hmrc.agentsubscription.service

import com.google.inject.Inject
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}

import javax.inject.Singleton
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeleteRecoveryStoreService @Inject() (
  config: Configuration,
  mongo: MongoComponent,
  mongoLockRepository: MongoLockRepository
)(implicit ec: ExecutionContext)
    extends Logging {
  private val lockService = LockService(mongoLockRepository, "agentSubscriptionDropAgentRecoveryStore", 15.minute)

  def dropCollection(): Future[Unit] =
    lockService
      .withLock {
        logger.warn("agent-recovery-store - lock obtained, dropping agent-recovery-store")

        mongo.database.getCollection("agent-recovery-store").drop().toFuture()

      }
      .map {
        case Some(_) =>
          mongo.database
            .listCollectionNames()
            .toFuture()
            .map(listOfCollection => logger.warn(s"New list of collection names:$listOfCollection"))
          logger.warn("agent-recovery-store collection dropped")

        case None => logger.warn("agent-recovery-store, lock not obtained")
      }

  if (config.get[Boolean]("mongo.drop-agent-recovery-store")) {
    dropCollection().recover { case e: Throwable =>
      logger.error("drop agent-recovery-store - failed", e)
    }
  } else {
    logger.warn("drop agent-recovery-store - disabled")
  }

}
