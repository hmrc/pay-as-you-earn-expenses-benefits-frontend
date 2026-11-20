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

package queries

import models.UserAnswers
import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsPath, Json}

class SettableSpec extends AnyFreeSpec with Matchers with TryValues {

  private object TestSettable extends Settable[String] {
    override def path: JsPath = JsPath \ "test"
  }

  private val userAnswers = UserAnswers("id", Json.obj("test" -> "foo"))

  "Settable" - {

    "cleanup must return the user answers unchanged by default when a value is set" in {
      TestSettable.cleanup(Some("foo"), userAnswers).success.value mustEqual userAnswers
    }

    "cleanup must return the user answers unchanged by default when a value is removed" in {
      TestSettable.cleanup(None, userAnswers).success.value mustEqual userAnswers
    }

    "must expose its path" in {
      TestSettable.path mustEqual JsPath \ "test"
    }
  }
}
