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

package views

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.data.Form
import play.api.data.Forms.{single, text}
import play.api.i18n.Messages
import play.api.test.Helpers.stubMessages

class ViewUtilsSpec extends AnyFreeSpec with Matchers {

  private implicit val messages: Messages = stubMessages()

  private val form: Form[String] = Form(single("value" -> text))

  private val formWithErrors: Form[String] = form.withError("value", "error.required")

  private val formWithGlobalErrors: Form[String] = form.withGlobalError("error.global")

  "ViewUtils" - {

    ".titleNoForm" - {

      "must build a title without a section" in {
        ViewUtils.titleNoForm("page.title") mustEqual "page.title - service.name - site.govuk"
      }

      "must build a title with a section" in {
        ViewUtils.titleNoForm("page.title", Some("page.section")) mustEqual "page.title - page.section - service.name - site.govuk"
      }
    }

    ".errorPrefix" - {

      "must be empty when the form has no errors" in {
        ViewUtils.errorPrefix(form) mustEqual ""
      }

      "must be the error prefix when the form has field errors" in {
        ViewUtils.errorPrefix(formWithErrors) mustEqual "error.title.prefix"
      }

      "must be the error prefix when the form has global errors" in {
        ViewUtils.errorPrefix(formWithGlobalErrors) mustEqual "error.title.prefix"
      }
    }

    ".title" - {

      "must build a title without an error prefix when the form has no errors" in {
        ViewUtils.title(form, "page.title") mustEqual " page.title - service.name - site.govuk"
      }

      "must build a title with an error prefix when the form has errors" in {
        ViewUtils.title(formWithErrors, "page.title") mustEqual "error.title.prefix page.title - service.name - site.govuk"
      }

      "must build a title with a section" in {
        ViewUtils.title(formWithErrors, "page.title", Some("page.section")) mustEqual
          "error.title.prefix page.title - page.section - service.name - site.govuk"
      }
    }
  }
}
