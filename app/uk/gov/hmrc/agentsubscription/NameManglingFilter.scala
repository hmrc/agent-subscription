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

package uk.gov.hmrc.agentsubscription
import javax.inject._
import play.api.GlobalSettings
import play.api.http._
import play.api.mvc.RequestHeader

class NameManglingFilter @Inject() (provider: Provider[GlobalSettings])
      extends GlobalSettingsHttpRequestHandler(provider) {

  override def handlerForRequest(request: RequestHeader) = {
    if (request.uri.startsWith("/agencies") || request.uri.startsWith("/sandbox")) {
      super.handlerForRequest(request.copy(path = "/agent-subscription" + request.path ))
    } else {
      super.handlerForRequest(request)
    }
  }
}
