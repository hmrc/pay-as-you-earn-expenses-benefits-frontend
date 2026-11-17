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
import config.FrontendAppConfig
import controllers.actions.{FakeFailingAuthConnector, FakeInternalIdAuthConnector}
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AuthConnector, BearerTokenExpired, MissingBearerToken}
import views.html.ActivateEnrolmentView

class ActivateEnrolmentControllerSpec extends SpecBase {

  private def app(connector: AuthConnector) =
    applicationBuilder(userAnswers = None)
      .overrides(bind[AuthConnector].toInstance(connector))
      .build()

  "ActivateEnrolment Controller" - {

    "must return OK and the correct view for a GET when the user is signed in" in {

      val application = app(new FakeInternalIdAuthConnector(Some("id")))

      running(application) {
        val request = FakeRequest(GET, routes.ActivateEnrolmentController.onPageLoad().url)

        val result = route(application, request).value

        val view = application.injector.instanceOf[ActivateEnrolmentView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view()(request, messages(application)).toString
      }
    }

    "must redirect to login when the user is not signed in" in {

      val application = app(new FakeFailingAuthConnector(new MissingBearerToken))

      running(application) {
        val appConfig = application.injector.instanceOf[FrontendAppConfig]
        val result    = route(application, FakeRequest(GET, routes.ActivateEnrolmentController.onPageLoad().url)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value must startWith(appConfig.loginUrl)
      }
    }

    "must redirect to login when the user's session has expired" in {

      val application = app(new FakeFailingAuthConnector(new BearerTokenExpired))

      running(application) {
        val appConfig = application.injector.instanceOf[FrontendAppConfig]
        val result    = route(application, FakeRequest(GET, routes.ActivateEnrolmentController.onPageLoad().url)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value must startWith(appConfig.loginUrl)
      }
    }
  }
}
