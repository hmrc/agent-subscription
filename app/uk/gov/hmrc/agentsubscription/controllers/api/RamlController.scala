/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.controllers.api

import javax.inject.{Inject, Singleton}

import uk.gov.hmrc.agentsubscription.views.txt
import controllers.AssetsBuilder
import play.api.http.HttpErrorHandler
import play.api.Configuration
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.libs.json.Json

case class ApiAccess(`type`: String, whitelistedApplicationIds: Seq[String])

object ApiAccess {
  implicit lazy val formats = Json.format[ApiAccess]
 }

@Singleton
class RamlController @Inject() (errorHandler: HttpErrorHandler, configuration: Configuration) extends AssetsBuilder(errorHandler) {

  private lazy val apiAccess = {
    val accessConfig = configuration.getConfig("api.access")
    val accessType = accessConfig.get.getString("type").getOrElse("PRIVATE")
    val whiteList = accessConfig.get.getStringSeq("white-list.applicationIds").getOrElse(Seq())
    ApiAccess(accessType, whiteList)
  }

  def definition(): Action[AnyContent] = Action {
    Ok(txt.definition(apiAccess)).withHeaders("Content-Type" -> "application/json")
  }

  def raml(version: String, file: String) = {
    super.at(s"/public/api/conf/$version", file)
  }
}
