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

package models

import org.scalatest.{OptionValues, TryValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import pages.QuestionPage
import play.api.libs.json._

import java.time.Instant
import scala.util.{Success, Try}

class UserAnswersSpec extends AnyFreeSpec with Matchers with OptionValues with TryValues {

  private case object TestPage extends QuestionPage[String] {
    override def path: JsPath = JsPath \ toString
    override def toString: String = "testPage"
  }

  private case object NestedPage extends QuestionPage[String] {
    override def path: JsPath = JsPath \ "testPage" \ "nested"
  }

  private case object OtherPage extends QuestionPage[String] {
    override def path: JsPath = JsPath \ toString
    override def toString: String = "otherPage"
  }

  private case object CleanupPage extends QuestionPage[String] {
    override def path: JsPath = JsPath \ toString
    override def toString: String = "cleanupPage"
    override def cleanup(value: Option[String], userAnswers: UserAnswers): Try[UserAnswers] =
      userAnswers.remove(OtherPage)
  }

  private val emptyAnswers = UserAnswers("id")

  "UserAnswers" - {

    ".get" - {

      "must return the value when it exists" in {
        val answers = emptyAnswers.copy(data = Json.obj("testPage" -> "foo"))
        answers.get(TestPage).value mustEqual "foo"
      }

      "must return None when the value does not exist" in {
        emptyAnswers.get(TestPage) mustBe None
      }
    }

    ".set" - {

      "must set the value" in {
        val result = emptyAnswers.set(TestPage, "foo").success.value
        result.get(TestPage).value mustEqual "foo"
      }

      "must overwrite an existing value" in {
        val result = emptyAnswers.set(TestPage, "foo").flatMap(_.set(TestPage, "bar")).success.value
        result.get(TestPage).value mustEqual "bar"
      }

      "must run the page's cleanup" in {
        val result = emptyAnswers
          .set(OtherPage, "foo")
          .flatMap(_.set(CleanupPage, "bar"))
          .success.value

        result.get(CleanupPage).value mustEqual "bar"
        result.get(OtherPage) mustBe None
      }

      "must fail when the path cannot be set" in {
        val answers = emptyAnswers.copy(data = Json.obj("testPage" -> 1))
        answers.set(NestedPage, "foo").failure.exception mustBe a[JsResultException]
      }
    }

    ".remove" - {

      "must remove an existing value" in {
        val result = emptyAnswers.set(TestPage, "foo").flatMap(_.remove(TestPage)).success.value
        result.get(TestPage) mustBe None
      }

      "must leave the data unchanged when the value does not exist" in {
        val answers = emptyAnswers.copy(data = Json.obj("otherPage" -> "foo"))
        answers.remove(TestPage).success.value.data mustEqual answers.data
      }

      "must run the page's cleanup" in {
        val result = emptyAnswers
          .set(OtherPage, "foo")
          .flatMap(_.remove(CleanupPage))
          .success.value

        result.get(OtherPage) mustBe None
      }
    }

    "json format" - {

      val instant = Instant.ofEpochMilli(1700000000000L)
      val answers = UserAnswers("id", Json.obj("testPage" -> "foo"), instant)

      "must write to the mongo format" in {
        val json = Json.toJson(answers)
        (json \ "_id").as[String] mustEqual "id"
        (json \ "data").as[JsObject] mustEqual Json.obj("testPage" -> "foo")
        (json \ "lastUpdated" \ "$date" \ "$numberLong").as[String] mustEqual "1700000000000"
      }

      "must read back what it writes" in {
        Json.toJson(answers).as[UserAnswers] mustEqual answers
      }
    }
  }
}
