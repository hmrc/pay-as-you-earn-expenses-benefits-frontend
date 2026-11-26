/*
 * Copyright 2026 HM Revenue & Customs
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

package services

import config.FrontendAppConfig
import connectors.{ClientExchangeConnector, PayeExbConnector}
import models.UserAnswers
import models.clientlist.*
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pages.ClientListRequestedAtPage
import repositories.SessionRepository
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClientListServiceSpec extends AnyFreeSpec with Matchers with ScalaFutures with MockitoSugar with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val now          = Instant.parse("2026-11-25T12:00:00Z")
  private val clock        = Clock.fixed(now, ZoneOffset.UTC)
  private val credentialId = "cred-id-1234"
  private val agentRef     = "AB1234"
  private val emptyAnswers = UserAnswers("id", lastUpdated = now)

  private val mockPayeExb    = mock[PayeExbConnector]
  private val mockExchange   = mock[ClientExchangeConnector]
  private val mockRepository = mock[SessionRepository]
  private val mockConfig     = mock[FrontendAppConfig]

  private val service = new ClientListService(mockPayeExb, mockExchange, mockRepository, mockConfig, clock)

  private val client         = PayeClient(1L, "Acme Ltd", None, 123, "123/AB45678", "123PA00123456", None, false)
  private val clientsResponse = GetAllClientsResponse(1, Seq(client), Seq("A"))

  override def beforeEach(): Unit = {
    reset(mockPayeExb, mockExchange, mockRepository, mockConfig)
    when(mockConfig.clientListGracePeriodSeconds).thenReturn(14400)
    when(mockRepository.set(any())).thenReturn(Future.successful(true))
  }

  private def answersRequestedAt(instant: Instant) = emptyAnswers.set(ClientListRequestedAtPage, instant).get

  private def savedAnswers: UserAnswers = {
    val captor = ArgumentCaptor.forClass(classOf[UserAnswers])
    verify(mockRepository, atLeastOnce()).set(captor.capture())
    captor.getValue
  }

  private def statuses(first: ClientListStatus, rest: ClientListStatus*) =
    when(mockPayeExb.getClientListStatus(any())(any())).thenReturn(Future.successful(first), rest.map(Future.successful)*)

  "getClientList" - {

    "when the status is -1" - {

      "must start a download, save the start time and return the rechecked status" in {
        statuses(ClientListStatus.InitiateDownload, ClientListStatus.InProgress)
        when(mockExchange.startClientListDownload(any())(any())).thenReturn(Future.successful(true))

        service.getClientList(emptyAnswers, credentialId, agentRef).futureValue mustBe ClientListResult.InProgress

        verify(mockExchange).startClientListDownload(credentialId)(hc)
        verify(mockPayeExb, times(2)).getClientListStatus(credentialId)(hc)
        savedAnswers.get(ClientListRequestedAtPage) mustBe Some(now)
      }

      "must return the clients when the recheck shows the download has finished" in {
        statuses(ClientListStatus.InitiateDownload, ClientListStatus.Succeeded)
        when(mockExchange.startClientListDownload(any())(any())).thenReturn(Future.successful(true))
        when(mockPayeExb.getAllClients(any())(any())).thenReturn(Future.successful(clientsResponse))

        service.getClientList(emptyAnswers, credentialId, agentRef).futureValue mustBe ClientListResult.HasClients(clientsResponse)
        savedAnswers.get(ClientListRequestedAtPage) mustBe None
      }

      "must return InProgress without starting another download when the recheck is still -1" in {
        statuses(ClientListStatus.InitiateDownload, ClientListStatus.InitiateDownload)
        when(mockExchange.startClientListDownload(any())(any())).thenReturn(Future.successful(true))

        service.getClientList(emptyAnswers, credentialId, agentRef).futureValue mustBe ClientListResult.InProgress
        verify(mockExchange, times(1)).startClientListDownload(any())(any())
      }

      "must return Failed when the exchange does not return 200" in {
        statuses(ClientListStatus.InitiateDownload)
        when(mockExchange.startClientListDownload(any())(any())).thenReturn(Future.successful(false))

        service.getClientList(emptyAnswers, credentialId, agentRef).futureValue mustBe ClientListResult.Failed
        verify(mockPayeExb, times(1)).getClientListStatus(any())(any())
        verify(mockRepository, never()).set(any())
      }
    }

    "when the status is 0" - {

      "must save the start time and return InProgress when no start time is stored" in {
        statuses(ClientListStatus.InProgress)

        service.getClientList(emptyAnswers, credentialId, agentRef).futureValue mustBe ClientListResult.InProgress
        savedAnswers.get(ClientListRequestedAtPage) mustBe Some(now)
        verify(mockExchange, never()).startClientListDownload(any())(any())
      }

      "must return InProgress when the grace period has not passed" in {
        statuses(ClientListStatus.InProgress)

        service.getClientList(answersRequestedAt(now.minusSeconds(14400)), credentialId, agentRef).futureValue mustBe ClientListResult.InProgress
        verify(mockRepository, never()).set(any())
        verify(mockExchange, never()).startClientListDownload(any())(any())
      }

      "must clear the start time and return Failed when the grace period has passed" in {
        statuses(ClientListStatus.InProgress)

        service.getClientList(answersRequestedAt(now.minusSeconds(14401)), credentialId, agentRef).futureValue mustBe ClientListResult.Failed
        savedAnswers.get(ClientListRequestedAtPage) mustBe None
        verify(mockExchange, never()).startClientListDownload(any())(any())
      }
    }

    "when the status is 1" - {

      "must return HasClients when there are clients and clear the start time" in {
        statuses(ClientListStatus.Succeeded)
        when(mockPayeExb.getAllClients(any())(any())).thenReturn(Future.successful(clientsResponse))

        service.getClientList(answersRequestedAt(now), credentialId, agentRef).futureValue mustBe ClientListResult.HasClients(clientsResponse)
        verify(mockPayeExb).getAllClients(GetAllClientsRequest(agentRef, credentialId))(hc)
        savedAnswers.get(ClientListRequestedAtPage) mustBe None
      }

      "must return NoClients when there are no clients without saving when nothing is stored" in {
        statuses(ClientListStatus.Succeeded)
        when(mockPayeExb.getAllClients(any())(any())).thenReturn(Future.successful(GetAllClientsResponse(0, Nil, Nil)))

        service.getClientList(emptyAnswers, credentialId, agentRef).futureValue mustBe ClientListResult.NoClients
        verify(mockRepository, never()).set(any())
      }

      "must fail when getting the clients fails" in {
        statuses(ClientListStatus.Succeeded)
        when(mockPayeExb.getAllClients(any())(any())).thenReturn(Future.failed(UpstreamErrorResponse("error", 500)))

        service.getClientList(emptyAnswers, credentialId, agentRef).failed.futureValue mustBe an[UpstreamErrorResponse]
      }
    }

    "when the status is 2" - {

      "must clear the start time and return Failed" in {
        statuses(ClientListStatus.Failed)

        service.getClientList(answersRequestedAt(now), credentialId, agentRef).futureValue mustBe ClientListResult.Failed
        savedAnswers.get(ClientListRequestedAtPage) mustBe None
      }
    }

    "must fail when getting the status fails" in {
      when(mockPayeExb.getClientListStatus(any())(any())).thenReturn(Future.failed(UpstreamErrorResponse("error", 503)))

      service.getClientList(emptyAnswers, credentialId, agentRef).failed.futureValue mustBe an[UpstreamErrorResponse]
    }
  }

  "retryDownload" - {

    "must start a new download and save the start time" in {
      when(mockExchange.startClientListDownload(any())(any())).thenReturn(Future.successful(true))

      service.retryDownload(answersRequestedAt(now.minusSeconds(20000)), credentialId).futureValue mustBe true
      savedAnswers.get(ClientListRequestedAtPage) mustBe Some(now)
    }

    "must return false when the exchange does not return 200" in {
      when(mockExchange.startClientListDownload(any())(any())).thenReturn(Future.successful(false))

      service.retryDownload(emptyAnswers, credentialId).futureValue mustBe false
      verify(mockRepository, never()).set(any())
    }
  }
}
