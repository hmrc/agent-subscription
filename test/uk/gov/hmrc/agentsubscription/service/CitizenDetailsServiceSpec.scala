/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.service

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import org.mockito.ArgumentMatchers.{ any, eq => eqs }
import org.mockito.Mockito.when
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.agentsubscription.connectors.CitizenDetailsConnector
import uk.gov.hmrc.agentsubscription.model.CitizenDetailsMatchResponse._
import uk.gov.hmrc.agentsubscription.model.{ CitizenDetailsRequest, DateOfBirth }
import uk.gov.hmrc.agentsubscription.support.ResettingMockitoSugar
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{ BadRequestException, HeaderCarrier }
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class CitizenDetailsServiceSpec extends UnitSpec with ResettingMockitoSugar with Eventually {

  val connector = resettingMock[CitizenDetailsConnector]

  val service = new CitizenDetailsService(connector)

  private implicit val hc = HeaderCarrier()

  val nino = Nino("XX212121B")
  val dobString = "12121990"
  val dtf = DateTimeFormatter.ofPattern("ddMMyyyy")
  val dob = DateOfBirth(LocalDate.parse(dobString, dtf))
  val request = CitizenDetailsRequest(nino, dob)

  "Citizen details service" should {
    "return Match if the Dob matches that in citizen details for a given nino" in {

      when(connector.getDateOfBirth(eqs(nino))(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful Some(dob))

      await(service.checkDetails(request)) shouldBe Match
    }

    "return NoMatch if the Dob does not match that in citizen details for a given nino" in {

      when(connector.getDateOfBirth(eqs(nino))(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful Some(dob))

      await(service.checkDetails(request.copy(dateOfBirth = DateOfBirth(LocalDate.now)))) shouldBe NoMatch
    }

    "return RecordNotFound if citizen details returns a BadRequest" in {

      when(connector.getDateOfBirth(eqs(nino))(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future failed new BadRequestException("nino not found"))

      val thrown = intercept[BadRequestException](
        await(service.checkDetails(request))).getMessage

      thrown shouldBe "nino not found"
    }
  }

}
