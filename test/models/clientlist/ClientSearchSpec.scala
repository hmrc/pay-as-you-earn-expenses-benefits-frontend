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

package models.clientlist

import models.UserAnswers
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import pages.ClientSearchPage
import play.api.libs.json.{JsString, Json}

class ClientSearchSpec extends AnyFreeSpec with Matchers {

  "ClientSearch" - {

    "must build a search with only the chosen value filled" in {
      ClientSearch(SearchType.EmployerName, "Acme") mustBe ClientSearch(Some("Acme"), None, None)
      ClientSearch(SearchType.EmployerPaye, "123/AB45678") mustBe ClientSearch(None, Some("123/AB45678"), None)
      ClientSearch(SearchType.ClientReference, "REF1") mustBe ClientSearch(None, None, Some("REF1"))
    }

    "must give the search type of the filled value" in {
      ClientSearch(Some("Acme"), None, None).searchType mustBe Some(SearchType.EmployerName)
      ClientSearch(None, Some("123/AB45678"), None).searchType mustBe Some(SearchType.EmployerPaye)
      ClientSearch(None, None, Some("REF1")).searchType mustBe Some(SearchType.ClientReference)
      ClientSearch(None, None, None).searchType mustBe None
    }

    "must write only the chosen value to json" in {
      Json.toJson(ClientSearch(None, Some("123/AB45678"), None)) mustBe Json.obj("employerPAYE" -> "123/AB45678")
    }

    "must be saved in user answers under clientSearch" in {
      val answers = UserAnswers("id").set(ClientSearchPage, ClientSearch(Some("Acme"), None, None)).get

      answers.data mustBe Json.obj("clientSearch" -> Json.obj("employerName" -> "Acme"))
      answers.get(ClientSearchPage) mustBe Some(ClientSearch(Some("Acme"), None, None))
    }
  }

  "SearchType" - {

    "must read and write its values" in {
      SearchType.values.foreach { searchType =>
        Json.toJson(searchType) mustBe JsString(searchType.toString)
        JsString(searchType.toString).as[SearchType] mustBe searchType
      }
    }
  }
}
