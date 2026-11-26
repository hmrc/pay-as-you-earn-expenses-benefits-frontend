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

package forms

import models.clientlist.{ClientSearch, SearchType}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.data.FormError

class ClientSearchFormProviderSpec extends AnyFreeSpec with Matchers {

  private val form = new ClientSearchFormProvider()()

  private def bind(data: (String, String)*) = form.bind(data.toMap)

  "ClientSearchFormProvider" - {

    "searchType" - {

      "must error when nothing is selected" in {
        bind().errors mustBe Seq(FormError("searchType", "clientSearch.error.searchType.required"))
      }

      "must error when an unknown search type is sent" in {
        bind("searchType" -> "unknown").errors mustBe Seq(FormError("searchType", "clientSearch.error.searchType.required"))
      }
    }

    "employerName" - {

      "must bind a valid employer name and trim it" in {
        bind("searchType" -> "employerName", "searchValue" -> "  Acme Ltd  ").value mustBe Some(ClientSearch(Some("Acme Ltd"), None, None))
      }

      "must bind an employer name of exactly 28 characters" in {
        bind("searchType" -> "employerName", "searchValue" -> ("a" * 28)).errors mustBe empty
      }

      "must error when the employer name is empty" in {
        bind("searchType" -> "employerName", "searchValue" -> " ").errors mustBe Seq(FormError("searchValue", "clientSearch.employerName.error.required"))
      }

      "must error when the employer name is missing" in {
        bind("searchType" -> "employerName").errors mustBe Seq(FormError("searchValue", "clientSearch.employerName.error.required"))
      }

      "must error when the employer name is longer than 28 characters" in {
        bind("searchType" -> "employerName", "searchValue" -> ("a" * 29)).errors mustBe Seq(FormError("searchValue", "clientSearch.employerName.error.length"))
      }
    }

    "employerPAYE" - {

      "must bind a valid PAYE reference" in {
        bind("searchType" -> "employerPAYE", "searchValue" -> "123/AB45678").value mustBe Some(ClientSearch(None, Some("123/AB45678"), None))
      }

      "must bind a PAYE reference with a 10 character reference" in {
        bind("searchType" -> "employerPAYE", "searchValue" -> "123/ABCDE12345").errors mustBe empty
      }

      "must error when the PAYE reference is empty" in {
        bind("searchType" -> "employerPAYE", "searchValue" -> "").errors mustBe Seq(FormError("searchValue", "clientSearch.employerPAYE.error.required"))
      }

      "must error when the PAYE reference is in the wrong format" in {
        Seq("12/AB45678", "1234/AB45678", "123AB45678", "123/", "123/ABCDE123456", "ABC/AB45678", "123/AB-456").foreach { value =>
          bind("searchType" -> "employerPAYE", "searchValue" -> value).errors mustBe Seq(FormError("searchValue", "clientSearch.employerPAYE.error.format"))
        }
      }
    }

    "clientReference" - {

      "must bind a valid client reference" in {
        bind("searchType" -> "clientReference", "searchValue" -> "REF1").value mustBe Some(ClientSearch(None, None, Some("REF1")))
      }

      "must bind a client reference of exactly 20 characters" in {
        bind("searchType" -> "clientReference", "searchValue" -> ("a" * 20)).errors mustBe empty
      }

      "must error when the client reference is empty" in {
        bind("searchType" -> "clientReference").errors mustBe Seq(FormError("searchValue", "clientSearch.clientReference.error.required"))
      }

      "must error when the client reference is longer than 20 characters" in {
        bind("searchType" -> "clientReference", "searchValue" -> ("a" * 21)).errors mustBe Seq(FormError("searchValue", "clientSearch.clientReference.error.length"))
      }
    }

    "must not check the search value when no search type is selected" in {
      bind("searchValue" -> ("a" * 50)).errors mustBe Seq(FormError("searchType", "clientSearch.error.searchType.required"))
    }

    "must fill the form from a saved search" in {
      val filled = form.fill(ClientSearch(None, Some("123/AB45678"), None))

      filled("searchType").value mustBe Some("employerPAYE")
      filled("searchValue").value mustBe Some("123/AB45678")
    }
  }
}
