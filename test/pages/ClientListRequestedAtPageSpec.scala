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

package pages

import models.UserAnswers
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import java.time.Instant

class ClientListRequestedAtPageSpec extends AnyFreeSpec with Matchers {

  "ClientListRequestedAtPage" - {

    "must set, get and remove the client list start time" in {
      val instant = Instant.parse("2026-11-25T12:00:00Z")
      val answers = UserAnswers("id").set(ClientListRequestedAtPage, instant).get

      answers.get(ClientListRequestedAtPage) mustBe Some(instant)
      (answers.data \ "clientListRequestedAt").isDefined mustBe true
      answers.remove(ClientListRequestedAtPage).get.get(ClientListRequestedAtPage) mustBe None
    }
  }
}
