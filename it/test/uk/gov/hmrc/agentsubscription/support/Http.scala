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

package uk.gov.hmrc.agentsubscription.support

import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSRequest
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.http.ws.WSHttpResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.Future
import scala.language.postfixOps

object Http {

  def get(
    url: String,
    headers: Seq[(String, String)] = Seq.empty
  )(implicit
    hc: HeaderCarrier,
    ws: WSClient
  ): HttpResponse =
    perform(url) { request =>
      request.withHttpHeaders(headers: _*).get()
    }

  def post(
    url: String,
    body: String,
    headers: Seq[(String, String)] = Seq.empty
  )(implicit
    hc: HeaderCarrier,
    ws: WSClient
  ): HttpResponse =
    perform(url) { request =>
      request.withHttpHeaders(headers: _*).post(body)
    }

  def put(
    url: String,
    body: String,
    headers: Seq[(String, String)] = Seq.empty
  )(implicit
    hc: HeaderCarrier,
    ws: WSClient
  ): HttpResponse =
    perform(url) { request =>
      request.withHttpHeaders(headers: _*).put(body)
    }

  def delete(url: String)(implicit
    hc: HeaderCarrier,
    ws: WSClient
  ): HttpResponse =
    perform(url) { request =>
      request.delete()
    }

  private def perform(
    url: String
  )(fun: WSRequest => Future[WSResponse])(implicit
    hc: HeaderCarrier,
    ws: WSClient
  ): HttpResponse = await(
    fun(
      ws.url(url)
        .withHttpHeaders(hc.headersForUrl(HeaderCarrier.Config())(url): _*)
        .withRequestTimeout(20000 milliseconds)
    ).map(WSHttpResponse(_))
  )

  private def await[A](future: Future[A]) = Await.result(future, Duration(10, SECONDS))

}

class Resource(
  path: String,
  port: Int
) {

  private def url = s"http://localhost:$port$path"

  def get()(implicit
    hc: HeaderCarrier = HeaderCarrier(),
    ws: WSClient
  ) = Http.get(url, Seq(HeaderNames.AUTHORIZATION -> "Bearer XYZ"))(hc, ws)

  def postAsJson(body: String)(implicit
    hc: HeaderCarrier = HeaderCarrier(),
    ws: WSClient
  ) =
    Http.post(
      url,
      body,
      Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON, HeaderNames.AUTHORIZATION -> "Bearer XYZ")
    )(
      hc,
      ws
    )

  def putAsJson(body: String)(implicit
    hc: HeaderCarrier = HeaderCarrier(),
    ws: WSClient
  ) =
    Http.put(
      url,
      body,
      Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON, HeaderNames.AUTHORIZATION -> "Bearer XYZ")
    )(
      hc,
      ws
    )

}
