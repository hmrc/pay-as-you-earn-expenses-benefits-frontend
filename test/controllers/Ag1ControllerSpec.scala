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

package controllers

import base.SpecBase
import models.UserAnswers
import org.mockito.ArgumentMatchers.{any, argThat}
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import repositories.SessionRepository
import controllers.actions.*
import controllers.actions.TestEnrolments.*
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolments}
import views.html.Ag1View

import scala.concurrent.Future

class Ag1ControllerSpec extends SpecBase with MockitoSugar {

  private def withRealAuth(connector: AuthConnector) =
    new GuiceApplicationBuilder()
      .overrides(
        bind[DataRetrievalAction].toInstance(new FakeDataRetrievalAction(Some(emptyUserAnswers))),
        bind[AuthConnector].toInstance(connector)
      )
      .build()

  "Ag1 Controller" - {

    "must create the session data and return OK when there is no session data yet" in {

      val mockSessionRepository = mock[SessionRepository]
      when(mockSessionRepository.set(any())) thenReturn Future.successful(true)

      val application =
        applicationBuilder(userAnswers = None)
          .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
          .build()

      running(application) {
        val result = route(application, FakeRequest(GET, routes.Ag1Controller.onPageLoad().url)).value

        status(result) mustEqual OK
        verify(mockSessionRepository, times(1)).set(argThat[UserAnswers](_.id == userAnswersId))
      }
    }

    "must not overwrite existing session data" in {

      val mockSessionRepository = mock[SessionRepository]

      val application =
        applicationBuilder(userAnswers = Some(emptyUserAnswers))
          .overrides(bind[SessionRepository].toInstance(mockSessionRepository))
          .build()

      running(application) {
        val result = route(application, FakeRequest(GET, routes.Ag1Controller.onPageLoad().url)).value

        status(result) mustEqual OK
        verify(mockSessionRepository, never()).set(any())
      }
    }

    "must return OK and the correct view for a GET" in {

      val application = applicationBuilder(userAnswers = Some(emptyUserAnswers)).build()

      running(application) {
        val request = FakeRequest(GET, routes.Ag1Controller.onPageLoad().url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[Ag1View]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(FakeAgentIdentifierAction.enrolmentReference.value)(request, messages(application)).toString
      }
    }

    "must return OK for an activated Agent using the real auth action" in {

      val application = withRealAuth(new FakeSuccessfulAuthConnector(Some("id"), Some(AffinityGroup.Agent), Enrolments(Set(payeAgent()))))

      running(application) {
        val result = route(application, FakeRequest(GET, routes.Ag1Controller.onPageLoad().url)).value

        status(result) mustEqual OK
      }
    }

    "must redirect an activated Organisation to ORG1" in {

      val application = withRealAuth(new FakeSuccessfulAuthConnector(Some("id"), Some(AffinityGroup.Organisation), Enrolments(Set(payeOrg()))))

      running(application) {
        val result = route(application, FakeRequest(GET, routes.Ag1Controller.onPageLoad().url)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual routes.Org1Controller.onPageLoad().url
      }
    }
  }
}
