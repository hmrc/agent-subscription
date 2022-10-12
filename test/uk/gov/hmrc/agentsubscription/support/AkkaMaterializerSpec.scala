/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.actor.ActorSystem
import akka.stream.testkit.NoMaterializer
import org.scalatest.{BeforeAndAfterAll, Suite}

/** Provides an implicit Materializer for use in tests. Note that if your test is starting an app (e.g. via
  * OneAppPerSuite or OneAppPerTest) then you should probably use the app's Materializer instead.
  */
trait AkkaMaterializerSpec extends UnitSpec with BeforeAndAfterAll { this: Suite =>

  implicit lazy val actorSystem = ActorSystem()
  implicit lazy val materializer = NoMaterializer

  override protected def afterAll(): Unit = {
    super.afterAll()
    val _ = actorSystem.terminate().futureValue
  }
}
