/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.config

import play.api.Configuration

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.Base64
import scala.util.matching.Regex

@Singleton
class AppConfig @Inject() (servicesConfig: ServicesConfig, config: Configuration) {

  val appName = "agent-subscription"

  def getConf(key: String) = servicesConfig.getString(key)

  val desBaseUrl = servicesConfig.baseUrl("des")
  val desEnvironment = getConf("microservice.services.des.environment")
  val desAuthToken = getConf("microservice.services.des.authorization-token")

  val taxEnrolmentsBaseUrl = servicesConfig.baseUrl("tax-enrolments")

  val enrolmentStoreProxyBaseUrl = servicesConfig.baseUrl("enrolment-store-proxy")

  val agentAssuranceBaseUrl = servicesConfig.baseUrl("agent-assurance")

  val agentOverseasApplicationBaseUrl = servicesConfig.baseUrl("agent-overseas-application")

  val citizenDetailsBaseUrl = servicesConfig.baseUrl("citizen-details")

  val emailBaseUrl = servicesConfig.baseUrl("email")

  val agentMappingBaseUrl = servicesConfig.baseUrl("agent-mapping")

  val mongodbSubscriptionJourneyTTL = servicesConfig.getInt("mongodb.subscriptionjourney.ttl")

  val companiesHouseApiProxyBaseUrl = servicesConfig.baseUrl("companies-house-api-proxy")

  val internalHostPatterns: Seq[Regex] = config.get[Seq[String]]("internalServiceHostPatterns").map(_.r)

  lazy val cryptoKey: String = Base64.getEncoder.encodeToString(md5(servicesConfig.getString("application.secret")))

  import java.security.MessageDigest

  private def md5(s: String) =
    MessageDigest.getInstance("MD5").digest(s.getBytes)

}
