/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL
import javax.inject.{Inject, Named, Singleton}

import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.{JsValue, Json}
import play.api.libs.json.Json.format
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.http.{HeaderCarrier, HttpPost, HttpPut, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

//TODO rename EnrolmentRequest2

case class KnownFact(key: String, value: String)
case class Legacy(previousVerifiers: List[KnownFact])
case class KnownFactsRequest(verifiers: List[KnownFact], legacy: Legacy)

object KnownFact {
  implicit val formatKf = format[KnownFact]
}

object Legacy {
  implicit val formatL = format[Legacy]
}

object KnownFactsRequest {
  implicit val formatKFR = format[KnownFactsRequest]
}

@Singleton
class EnrolmentStoreConnector @Inject() (@Named("enrolment-store-baseUrl") baseUrl: URL, httpPut: HttpPut, metrics:Metrics) extends HttpAPIMonitor {
  override val kenshooRegistry = metrics.defaultRegistry

  def sendKnownFacts(arn: String, postcode: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Integer] = {
    val request = KnownFactsRequest(List(KnownFact("arn",arn), KnownFact("postcode",postcode)), Legacy(List.empty))

    //TODO get correct name??? - GGW-AddKnownFacts-HMRC-AS-AGENT-POST
    monitor("GGW-AddKnownFacts-HMRC-AS-AGENT-POST") {
      httpPut.PUT[JsValue, HttpResponse](s"""${baseUrl}/enrolment-store/enrolments/HMRC-AS-AGENT""", Json.toJson(request)) map {
        response => response.status
      }
    }
  }

  def enrol(): Unit ={

  }

}
