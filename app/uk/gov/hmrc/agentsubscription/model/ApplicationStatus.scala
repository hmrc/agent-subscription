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

package uk.gov.hmrc.agentsubscription.model

import play.api.libs.json._

sealed trait ApplicationStatus
extends Product
with Serializable {

  val key: String =
    this match { // status can only progress in acceding order indicated below
      case ApplicationStatus.Pending => "pending" // 1
      case ApplicationStatus.Rejected => "rejected" // 2
      case ApplicationStatus.Accepted => "accepted" // 2
      case ApplicationStatus.AttemptingRegistration => "attempting_registration" // 3
      case ApplicationStatus.Registered => "registered" // 4
      case ApplicationStatus.Complete => "complete" // 5
    }
}

object ApplicationStatus {

  case object Pending
  extends ApplicationStatus

  case object Rejected
  extends ApplicationStatus

  case object Accepted
  extends ApplicationStatus

  case object AttemptingRegistration
  extends ApplicationStatus

  case object Registered
  extends ApplicationStatus

  case object Complete
  extends ApplicationStatus

  def apply(typeIdentifier: String): ApplicationStatus =
    typeIdentifier match {

      case ApplicationStatus.Pending.key => ApplicationStatus.Pending
      case ApplicationStatus.Rejected.key => ApplicationStatus.Rejected
      case ApplicationStatus.Accepted.key => ApplicationStatus.Accepted
      case ApplicationStatus.AttemptingRegistration.key => ApplicationStatus.AttemptingRegistration
      case ApplicationStatus.Registered.key => ApplicationStatus.Registered
      case ApplicationStatus.Complete.key => ApplicationStatus.Complete
      case other => throw new RuntimeException(s"application status $other not known")
    }

  def unapply(arg: ApplicationStatus): Option[String] = Some(arg.key)

  implicit val reads: Reads[ApplicationStatus] =
    new Reads[ApplicationStatus] {
      override def reads(json: JsValue): JsResult[ApplicationStatus] =
        json match {
          case JsString(ApplicationStatus.Pending.key) => JsSuccess(Pending)
          case JsString(ApplicationStatus.Accepted.key) => JsSuccess(Accepted)
          case JsString(ApplicationStatus.Rejected.key) => JsSuccess(Rejected)
          case JsString(ApplicationStatus.AttemptingRegistration.key) => JsSuccess(AttemptingRegistration)
          case JsString(ApplicationStatus.Registered.key) => JsSuccess(Registered)
          case JsString(ApplicationStatus.Complete.key) => JsSuccess(Complete)
          case invalid => JsError(s"Invalid ApplicationStatus found: $invalid")
        }
    }

  implicit val writes: Writes[ApplicationStatus] =
    new Writes[ApplicationStatus] {
      override def writes(o: ApplicationStatus): JsValue = JsString(o.key)
    }

  val ActiveStatuses = Seq(
    Pending,
    Accepted,
    AttemptingRegistration,
    Registered,
    Complete
  )

}
