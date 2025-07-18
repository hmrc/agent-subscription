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

package uk.gov.hmrc.agentsubscription.binders

import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.AuthProviderId
import uk.gov.hmrc.agentsubscription.model.Crn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.Vrn

object UrlBinders {

  implicit val utrBinder: SimpleObjectBinder[Utr] = new SimpleObjectBinder[Utr](Utr.apply, _.value)
  implicit val crnBinder: SimpleObjectBinder[Crn] = new SimpleObjectBinder[Crn](Crn.apply, _.value)
  implicit val vrnBinder: SimpleObjectBinder[Vrn] = new SimpleObjectBinder[Vrn](Vrn.apply, _.value)
  implicit val authProviderIdBinder: SimpleObjectBinder[AuthProviderId] = new SimpleObjectBinder[AuthProviderId](AuthProviderId.apply, _.id)
  implicit val ninoBinder: SimpleObjectBinder[Nino] = new SimpleObjectBinder[Nino](Nino.apply, _.value)

}
