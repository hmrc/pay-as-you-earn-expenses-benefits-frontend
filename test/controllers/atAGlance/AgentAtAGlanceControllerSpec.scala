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

package controllers.atAGlance

import base.SpecBase
import controllers.actions.FakeAgentIdentifierAction
import forms.ClientSearchFormProvider
import models.UserAnswers
import models.clientlist.*
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, argThat, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar
import pages.ClientSearchPage
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.SessionRepository
import services.ClientListService
import uk.gov.hmrc.http.UpstreamErrorResponse
import views.html.agent.*

import scala.concurrent.Future

class AgentAtAGlanceControllerSpec extends SpecBase with MockitoSugar {

  private val agentReference = FakeAgentIdentifierAction.enrolmentReference.value
  private val credentialId   = "cred-id-1234"
  private val form           = new ClientSearchFormProvider()()

  private val client          = PayeClient(1L, "Acme Ltd", None, 123, "123/AB45678", "123PA00123456", None, false)
  private val clientsResponse = GetAllClientsResponse(1, Seq(client), Seq("A"))

  private lazy val onPageLoadUrl = controllers.atAGlance.routes.AgentAtAGlanceController.onPageLoad().url
  private lazy val tryAgainUrl   = controllers.atAGlance.routes.AgentAtAGlanceController.tryAgain().url

  private def setup(userAnswers: Option[UserAnswers] = Some(emptyUserAnswers)) = {
    val mockService    = mock[ClientListService]
    val mockRepository = mock[SessionRepository]
    when(mockRepository.set(any())).thenReturn(Future.successful(true))

    val application = applicationBuilder(userAnswers)
      .overrides(
        bind[ClientListService].toInstance(mockService),
        bind[SessionRepository].toInstance(mockRepository)
      )
      .build()

    (application, mockService, mockRepository)
  }

  private def clientListReturns(service: ClientListService, result: ClientListResult) =
    when(service.getClientList(any(), any(), any())(any())).thenReturn(Future.successful(result))

  "AgentAtAGlance Controller" - {

    "onPageLoad" - {

      "must show the clients view when the agent has clients" in {
        val (application, service, _) = setup()
        clientListReturns(service, ClientListResult.HasClients(clientsResponse))

        running(application) {
          val request = FakeRequest(GET, onPageLoadUrl)
          val result  = route(application, request).value
          val view    = application.injector.instanceOf[AgentClientsView]

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(form, agentReference)(request, messages(application)).toString
          verify(service).getClientList(argThat[UserAnswers](_.id == userAnswersId), eqTo(credentialId), eqTo(agentReference))(any())
        }
      }

      "must fill the search form from a saved search" in {
        val search  = ClientSearch(None, Some("123/AB45678"), None)
        val answers = emptyUserAnswers.set(ClientSearchPage, search).success.value
        val (application, service, _) = setup(Some(answers))
        clientListReturns(service, ClientListResult.HasClients(clientsResponse))

        running(application) {
          val request = FakeRequest(GET, onPageLoadUrl)
          val result  = route(application, request).value
          val view    = application.injector.instanceOf[AgentClientsView]

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(form.fill(search), agentReference)(request, messages(application)).toString
        }
      }

      "must show the no clients view when the agent has no clients" in {
        val (application, service, _) = setup()
        clientListReturns(service, ClientListResult.NoClients)

        running(application) {
          val request = FakeRequest(GET, onPageLoadUrl)
          val result  = route(application, request).value
          val view    = application.injector.instanceOf[AgentNoClientsView]

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(agentReference)(request, messages(application)).toString
        }
      }

      "must show the in progress view when the client list is being downloaded" in {
        val (application, service, _) = setup()
        clientListReturns(service, ClientListResult.InProgress)

        running(application) {
          val request = FakeRequest(GET, onPageLoadUrl)
          val result  = route(application, request).value
          val view    = application.injector.instanceOf[AgentClientListInProgressView]

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(agentReference)(request, messages(application)).toString
        }
      }

      "must show the failed view when the client list could not be downloaded" in {
        val (application, service, _) = setup()
        clientListReturns(service, ClientListResult.Failed)

        running(application) {
          val request = FakeRequest(GET, onPageLoadUrl)
          val result  = route(application, request).value
          val view    = application.injector.instanceOf[AgentClientListFailedView]

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(agentReference)(request, messages(application)).toString
        }
      }

      "must fail when paye-exb returns an error" in {
        val (application, service, _) = setup()
        when(service.getClientList(any(), any(), any())(any())).thenReturn(Future.failed(UpstreamErrorResponse("error", 500)))

        running(application) {
          route(application, FakeRequest(GET, onPageLoadUrl)).value.failed.futureValue mustBe an[UpstreamErrorResponse]
        }
      }

      "must create the session data when there is none" in {
        val (application, service, repository) = setup(None)
        clientListReturns(service, ClientListResult.InProgress)

        running(application) {
          status(route(application, FakeRequest(GET, onPageLoadUrl)).value) mustEqual OK
          verify(repository).set(argThat[UserAnswers](answers => answers.id == userAnswersId && answers.data.fields.isEmpty))
        }
      }

      "must not save the session data when it already exists" in {
        val (application, service, repository) = setup()
        clientListReturns(service, ClientListResult.InProgress)

        running(application) {
          status(route(application, FakeRequest(GET, onPageLoadUrl)).value) mustEqual OK
          verify(repository, never()).set(any())
        }
      }
    }

    "onSubmit" - {

      "must save the search and redirect to the clients page when the search is valid" in {
        val (application, service, repository) = setup()

        running(application) {
          val request = FakeRequest(POST, controllers.atAGlance.routes.AgentAtAGlanceController.onSubmit().url)
            .withFormUrlEncodedBody("searchType" -> "employerPAYE", "searchValue" -> "123/AB45678")
          val result = route(application, request).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual controllers.atAGlance.routes.AgentClientsController.onPageLoad().url

          val captor = ArgumentCaptor.forClass(classOf[UserAnswers])
          verify(repository).set(captor.capture())
          captor.getValue.get(ClientSearchPage) mustBe Some(ClientSearch(None, Some("123/AB45678"), None))
          verify(service, never()).getClientList(any(), any(), any())(any())
        }
      }

      "must save the search when there is no session data yet" in {
        val (application, _, repository) = setup(None)

        running(application) {
          val request = FakeRequest(POST, controllers.atAGlance.routes.AgentAtAGlanceController.onSubmit().url)
            .withFormUrlEncodedBody("searchType" -> "employerName", "searchValue" -> "Acme")
          status(route(application, request).value) mustEqual SEE_OTHER

          val captor = ArgumentCaptor.forClass(classOf[UserAnswers])
          verify(repository).set(captor.capture())
          captor.getValue.id mustBe userAnswersId
          captor.getValue.get(ClientSearchPage) mustBe Some(ClientSearch(Some("Acme"), None, None))
        }
      }

      "must return a Bad Request and the errors when the search is invalid" in {
        val (application, service, repository) = setup()
        clientListReturns(service, ClientListResult.HasClients(clientsResponse))

        running(application) {
          val request = FakeRequest(POST, controllers.atAGlance.routes.AgentAtAGlanceController.onSubmit().url)
            .withFormUrlEncodedBody("searchType" -> "employerPAYE", "searchValue" -> "bad")
          val result    = route(application, request).value
          val view      = application.injector.instanceOf[AgentClientsView]
          val boundForm = form.bind(Map("searchType" -> "employerPAYE", "searchValue" -> "bad"))

          status(result) mustEqual BAD_REQUEST
          contentAsString(result) mustEqual view(boundForm, agentReference)(request, messages(application)).toString
          verify(repository, never()).set(any())
        }
      }

      "must show the current client list page when the search is invalid and there are no longer clients to search" in {
        val (application, service, _) = setup()
        clientListReturns(service, ClientListResult.InProgress)

        running(application) {
          val request = FakeRequest(POST, controllers.atAGlance.routes.AgentAtAGlanceController.onSubmit().url).withFormUrlEncodedBody()
          val result  = route(application, request).value
          val view    = application.injector.instanceOf[AgentClientListInProgressView]

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(agentReference)(request, messages(application)).toString
        }
      }
    }

    "tryAgain" - {

      "must start a new download and redirect to the at a glance page" in {
        val (application, service, _) = setup()
        when(service.retryDownload(any(), any())(any())).thenReturn(Future.successful(true))

        running(application) {
          val result = route(application, FakeRequest(GET, tryAgainUrl)).value

          status(result) mustEqual SEE_OTHER
          redirectLocation(result).value mustEqual onPageLoadUrl
          verify(service).retryDownload(argThat[UserAnswers](_.id == userAnswersId), eqTo(credentialId))(any())
        }
      }

      "must start a new download when there is no session data yet" in {
        val (application, service, _) = setup(None)
        when(service.retryDownload(any(), any())(any())).thenReturn(Future.successful(true))

        running(application) {
          status(route(application, FakeRequest(GET, tryAgainUrl)).value) mustEqual SEE_OTHER
          verify(service).retryDownload(argThat[UserAnswers](_.id == userAnswersId), eqTo(credentialId))(any())
        }
      }

      "must show the failed view when the download could not be started" in {
        val (application, service, _) = setup()
        when(service.retryDownload(any(), any())(any())).thenReturn(Future.successful(false))

        running(application) {
          val request = FakeRequest(GET, tryAgainUrl)
          val result  = route(application, request).value
          val view    = application.injector.instanceOf[AgentClientListFailedView]

          status(result) mustEqual OK
          contentAsString(result) mustEqual view(agentReference)(request, messages(application)).toString
        }
      }
    }
  }
}
