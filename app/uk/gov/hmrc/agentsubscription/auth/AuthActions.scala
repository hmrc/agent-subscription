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

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import play.api.mvc._
import uk.gov.hmrc.agentsubscription.connectors.AuthConnector

import scala.concurrent.Future
import uk.gov.hmrc.play.HeaderCarrierConverter

trait AuthActions {
  me: Results =>

  val authConnector: AuthConnector

  protected val withAgentAffinityGroup = new ActionBuilder[RequestWithAuthority] with ActionRefiner[Request, RequestWithAuthority] {
    protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithAuthority[A]]] = {
      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
      authConnector.currentAuthority() map {
        case authority@Some(Authority(_, _, _, "Agent", _)) => Right(RequestWithAuthority(authority.get, request))
        case _ => Left(Unauthorized)
      }
    }
  }

  protected val withAgentAffinityGroupAndEnrolments = withAgentAffinityGroup andThen new ActionRefiner[RequestWithAuthority, RequestWithEnrolments] {
    override protected def refine[A](request: RequestWithAuthority[A]): Future[Either[Result, RequestWithEnrolments[A]]] = {
      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
      authConnector.enrolments(request.authority) map { e =>
        Right(RequestWithEnrolments(e, request))
      }
    }
  }

}

case class RequestWithAuthority[+A](authority: Authority, request: Request[A]) extends WrappedRequest[A](request)

case class RequestWithEnrolments[+A](enrolments: List[Enrolment], request: Request[A]) extends WrappedRequest[A](request)
