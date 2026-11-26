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
import models.clientlist.ClientSearch
import pages.ClientSearchPage
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import views.html.agent.AgentClientsPlaceholderView

class AgentClientsControllerSpec extends SpecBase {

  "AgentClients Controller" - {

    "must return OK and show the saved search" in {
      val search      = ClientSearch(Some("Acme"), None, None)
      val answers     = emptyUserAnswers.set(ClientSearchPage, search).success.value
      val application = applicationBuilder(Some(answers)).build()

      running(application) {
        val request = FakeRequest(GET, controllers.atAGlance.routes.AgentClientsController.onPageLoad().url)
        val result  = route(application, request).value
        val view    = application.injector.instanceOf[AgentClientsPlaceholderView]

        status(result) mustEqual OK
        contentAsString(result) mustEqual view(Some(search), "AB1234")(request, messages(application)).toString
        contentAsString(result) must include("Acme")
      }
    }

    "must redirect to Journey Recovery when there is no session data" in {
      val application = applicationBuilder(None).build()

      running(application) {
        val result = route(application, FakeRequest(GET, controllers.atAGlance.routes.AgentClientsController.onPageLoad().url)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustEqual controllers.routes.JourneyRecoveryController.onPageLoad().url
      }
    }
  }
}
