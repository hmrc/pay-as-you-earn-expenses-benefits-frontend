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
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class AgentPlaceholderControllerSpec extends SpecBase {

  "AgentPlaceholder Controller" - {

    Seq(
      controllers.atAGlance.routes.AgentPlaceholderController.downloadClients().url   -> "Download your client list",
      controllers.atAGlance.routes.AgentPlaceholderController.filingChoices().url     -> "Filing only service",
      controllers.atAGlance.routes.AgentPlaceholderController.noticeOptions().url     -> "Notice options",
      controllers.atAGlance.routes.AgentPlaceholderController.notificationEmail().url -> "Notifications email"
    ).foreach { case (url, heading) =>

      s"must return OK and show the $heading placeholder" in {
        val application = applicationBuilder().build()

        running(application) {
          val result = route(application, FakeRequest(GET, url)).value

          status(result) mustEqual OK
          contentAsString(result) must include(s"""<h1 class="govuk-heading-xl">$heading</h1>""")
        }
      }
    }
  }
}
