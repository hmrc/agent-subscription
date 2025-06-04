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

package uk.gov.hmrc.agentsubscription.utils

import play.api.Logging
import uk.gov.hmrc.http.BadGatewayException
import uk.gov.hmrc.http.GatewayTimeoutException
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object Retry
extends Logging {

  // TODO replace this with exponential backoff retries (from "com.softwaremill.retry" %% "retry" % "0.3.0")
  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def retry[A](n: Int)(f: => Future[A])(implicit ec: ExecutionContext): Future[A] = f.recoverWith {
    case ShouldRetryAfter(e) if n > 1 =>
      logger.warn(s"Retrying after failure $e")
      retry(n - 1)(f)
  }

  private object ShouldRetryAfter {
    def unapply(e: Exception): Option[Exception] =
      e match {
        case ex: GatewayTimeoutException => Some(ex)
        case ex: BadGatewayException => Some(ex)
        case ex @ UpstreamErrorResponse(_, _, _, _) => Some(ex)
        case _ => None
      }
  }

}
