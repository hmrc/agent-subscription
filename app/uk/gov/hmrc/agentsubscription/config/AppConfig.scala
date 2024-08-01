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

package uk.gov.hmrc.agentsubscription.config

import play.api.Configuration

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.util.matching.Regex

@Singleton
class AppConfig @Inject() (servicesConfig: ServicesConfig, config: Configuration) {

  val appName = "agent-subscription"

  def getConf(key: String): String = servicesConfig.getString(key)

  val desBaseUrl: String = servicesConfig.baseUrl("des")
  val desEnvironment: String = getConf("microservice.services.des.environment")
  val desAuthToken: String = getConf("microservice.services.des.authorization-token")

  val taxEnrolmentsBaseUrl: String = servicesConfig.baseUrl("tax-enrolments")

  val enrolmentStoreProxyBaseUrl: String = servicesConfig.baseUrl("enrolment-store-proxy")

  val agentAssuranceBaseUrl: String = servicesConfig.baseUrl("agent-assurance")

  val agentOverseasApplicationBaseUrl: String = servicesConfig.baseUrl("agent-overseas-application")

  val citizenDetailsBaseUrl: String = servicesConfig.baseUrl("citizen-details")

  val emailBaseUrl: String = servicesConfig.baseUrl("email")

  val agentMappingBaseUrl: String = servicesConfig.baseUrl("agent-mapping")

  val mongodbSubscriptionJourneyTTL: Long = servicesConfig.getInt("mongodb.subscriptionjourney.ttl")

  val companiesHouseApiProxyBaseUrl: String = servicesConfig.baseUrl("companies-house-api-proxy")

  val internalHostPatterns: Seq[Regex] = config.get[Seq[String]]("internalServiceHostPatterns").map(_.r)

}
