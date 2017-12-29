/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.utils

import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FutureUtilsSpec extends UnitSpec {

  "FutureUtilsSpec" should {
    "return result when the operation succeeds" in {
      def op = Future {
        100
      }

      val result = await(FutureUtils.retry(3)(op))

      result shouldBe 100
    }

    "retry 3 times the given the operation fails all the time" in {
      var trials = 0

      an[IllegalStateException] should be thrownBy {
        await(FutureUtils.retry(3) {
          Future {
            trials = trials + 1
            throw new IllegalStateException(s"future failed $trials")
          }
        })
      }

      trials shouldBe 3
    }

    "succeed on third trial after retrying twice" in {
      var trials = 0

      val result = await(FutureUtils.retry(3) {
        Future {
          trials = trials + 1
          if (trials <= 2)
            throw new IllegalStateException(s"future failed $trials")
          else
            100
        }
      })

      result shouldBe 100
      trials shouldBe 3
    }

    "return exception after retrying 3 times the given operation in wake of failure" in {
      def op = Future {
        throw new IllegalStateException("future failed")
      }

      an[IllegalStateException] should be thrownBy {
        await(FutureUtils.retry(3)(op))
      }
    }

  }
}
