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
import uk.gov.hmrc.crypto.json.JsonEncryption.stringEncrypter
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter}

case class VerifiedEmails(emails: Set[String] = Set.empty, encrypted: Option[Boolean] = None)

object VerifiedEmails {
  def databaseFormat(implicit crypto: Encrypter with Decrypter): Format[VerifiedEmails] = {

    def reads(json: JsValue): JsResult[VerifiedEmails] =
      for {
        isEncrypted <- (json \ "encrypted").validateOpt[Boolean]
        emails = isEncrypted match {
                   case Some(true) =>
                     (json \ "emails")
                       .validate[Set[String]] match {
                       case JsSuccess(emails, _) => emails.map(str => crypto.decrypt(Crypted(str)).value)
                       case JsError(_)           => Set[String]()
                     }
                   case _ =>
                     (json \ "emails").validate[Set[String]] match {
                       case JsSuccess(emails, _) => emails
                       case JsError(_)           => Set[String]()
                     }
                 }
      } yield VerifiedEmails(emails, isEncrypted)

    def writes(verifiedEmails: VerifiedEmails): JsValue =
      Json.obj(
        "emails"    -> verifiedEmails.emails.map(stringEncrypter.writes),
        "encrypted" -> Some(true)
      )

    Format(reads(_), verifiedEmails => writes(verifiedEmails))
  }

  implicit val writes: Writes[VerifiedEmails] = Json.writes[VerifiedEmails]
}
