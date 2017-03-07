/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.auth

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import uk.gov.hmrc.agentsubscription.connectors.AuthConnector
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait AuthActions {
  me: Results =>

  val authConnector: AuthConnector

  protected val agentWithEnrolments = new ActionBuilder[RequestWithEnrolments] with ActionRefiner[Request, RequestWithEnrolments] {
    protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithEnrolments[A]]] = {
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
      authConnector.currentAuthority() flatMap {
        case Some(Authority("Agent", enrolmentsUrl)) =>
          authConnector.enrolments(enrolmentsUrl) map { e =>
            Right(RequestWithEnrolments(e, request))
          }
        case _ => Future successful Left(Unauthorized)
      }
    }
  }

  case class RequestWithEnrolments[A](enrolments: List[Enrolment], request: Request[A]) extends WrappedRequest[A](request)

}
