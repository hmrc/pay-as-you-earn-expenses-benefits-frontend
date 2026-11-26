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

package views.agent

import base.SpecBase
import forms.ClientSearchFormProvider
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.Application
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import views.html.agent.*

import scala.jdk.CollectionConverters.*

class AgentViewsSpec extends SpecBase {

  private val agentReference = "AB1234"

  private lazy val application: Application = applicationBuilder().build()

  private implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  private def doc(html: Html): Document = Jsoup.parse(html.toString)

  private def navItems(document: Document): Seq[(String, String)] =
    document.select(".govuk-service-navigation__item a").asScala.toSeq.map(a => a.text -> a.attr("href"))

  private def href(document: Document, id: String): String = document.getElementById(id).attr("href")

  private val atAGlance  = "At a glance" -> "/paye-expenses-and-benefits/agent"
  private val filingOnly = "Filing only service" -> "/paye-expenses-and-benefits/agent/filing-choices"
  private val faqs       = "FAQs" -> "/information/faqs/paye"

  private def filingOnlyServiceSection(document: Document) = {
    document.select("h2").asScala.map(_.text) must contain("Filing only service")
    href(document, "file-a-return") mustBe "/paye-file/agent/AB1234"
    href(document, "payroll-software") mustBe "https://www.gov.uk/payroll-software"
  }

  private def commonChecks(document: Document, heading: String) = {
    document.select("h1").text mustBe heading
    document.getElementById("agent-reference").text mustBe "PAYE Agent ID: AB1234"
    document.select(".govuk-service-navigation__service-name").text mustBe "Pay As You Earn"
    document.select("meta[http-equiv=refresh]").size mustBe 0
    document.select(".govuk-service-navigation__item--active a").text mustBe "At a glance"
    filingOnlyServiceSection(document)
  }

  "AgentClientsView" - {

    val form = new ClientSearchFormProvider()()
    def view = application.injector.instanceOf[AgentClientsView]

    "must show the clients page with every section and the full navigation" in {
      val document = doc(view(form, agentReference)(request, messages(application)))

      commonChecks(document, "Your PAYE clients")
      document.title must startWith("Your PAYE clients")

      navItems(document) mustBe Seq(
        atAGlance,
        "Clients" -> "/paye-expenses-and-benefits/agent/clients",
        filingOnly,
        "Recent notices" -> "/notice-view/agent/AB1234/recent-notices",
        "Notice options" -> "/paye-expenses-and-benefits/agent/notice-options",
        "Notifications email" -> "/paye-expenses-and-benefits/agent/notification-email",
        faqs
      )

      document.select("h2").asScala.map(_.text) must contain allOf("Find a client", "Notices", "How you get notices")
      document.select("form").attr("action") mustBe "/paye-expenses-and-benefits/agent"
      document.select("form").attr("method") mustBe "POST"
      href(document, "view-all-clients") mustBe "/paye-expenses-and-benefits/agent/clients"
      href(document, "download-clients") mustBe "/paye-expenses-and-benefits/agent/clients/download"
      href(document, "recent-notices") mustBe "/notice-view/agent/AB1234/recent-notices"
      href(document, "notice-options") mustBe "/paye-expenses-and-benefits/agent/notice-options"
      href(document, "notification-email") mustBe "/paye-expenses-and-benefits/agent/notification-email"
      document.select(".govuk-details__summary-text").text mustBe "Help with finding a client"
    }

    "must show the three search types with none selected" in {
      val document = doc(view(form, agentReference)(request, messages(application)))
      val radios   = document.select("input[name=searchType]").asScala.toSeq

      radios.map(_.attr("value")) mustBe Seq("employerName", "employerPAYE", "clientReference")
      radios.exists(_.hasAttr("checked")) mustBe false
      document.select("label[for=searchValue]").text mustBe "Search for"
      document.getElementById("searchValue-hint").text mustBe "For example, Hedgehog Woodshop or 754/KG12677"
      document.select(".govuk-error-summary").size mustBe 0
    }

    "must keep the chosen search and show the errors when the search is invalid" in {
      val boundForm = form.bind(Map("searchType" -> "employerPAYE", "searchValue" -> "bad"))
      val document  = doc(view(boundForm, agentReference)(request, messages(application)))

      document.title must startWith("Error: ")
      document.select(".govuk-error-summary a").attr("href") mustBe "#searchValue"
      document.select(".govuk-error-summary a").text mustBe "Enter the employer PAYE reference in the correct format, like 123/AB45678"
      document.getElementById("searchValue-error").text must include("Enter the employer PAYE reference in the correct format, like 123/AB45678")
      document.select("input[name=searchType][checked]").attr("value") mustBe "employerPAYE"
      document.getElementById("searchValue").attr("value") mustBe "bad"
    }

    "must link the search type error to the first radio" in {
      val document = doc(view(form.bind(Map.empty[String, String]), agentReference)(request, messages(application)))

      document.select(".govuk-error-summary a").attr("href") mustBe "#searchType"
      document.getElementById("searchType") must not be null
      document.getElementById("searchType-error").text must include("Select what you want to search by")
    }
  }

  "AgentNoClientsView" - {

    "must show the no clients page with the request authorisation navigation" in {
      val document = doc(application.injector.instanceOf[AgentNoClientsView].apply(agentReference)(request, messages(application)))

      commonChecks(document, "You do not have any authorised PAYE clients")
      navItems(document) mustBe Seq(
        atAGlance,
        "Request authorisation" -> "https://www.gov.uk/guidance/client-authorisation-an-overview",
        filingOnly,
        faqs
      )
      href(document, "request-authorisation") mustBe "https://www.gov.uk/guidance/client-authorisation-an-overview"
      document.select(".govuk-list--bullet li").asScala.map(_.text) mustBe Seq(
        "ask a client to authorise you online",
        "send us form FBI-2 signed by your client"
      )
    }
  }

  "AgentClientListInProgressView" - {

    "must show the in progress page with a button that reloads the page" in {
      val document = doc(application.injector.instanceOf[AgentClientListInProgressView].apply(agentReference)(request, messages(application)))

      commonChecks(document, "We are getting your client list")
      navItems(document) mustBe Seq(atAGlance, filingOnly, faqs)

      val button = document.getElementById("check-again")
      button.text mustBe "Check if your client list is ready"
      button.attr("href") mustBe "/paye-expenses-and-benefits/agent"
      button.hasClass("govuk-button") mustBe true
      document.select("script").asScala.exists(_.data.contains("location.reload")) mustBe false
    }
  }

  "AgentClientListFailedView" - {

    "must show the failed page with a try again button and the helpdesk link" in {
      val document = doc(application.injector.instanceOf[AgentClientListFailedView].apply(agentReference)(request, messages(application)))

      commonChecks(document, "Sorry, we could not get your client list")
      navItems(document) mustBe Seq(atAGlance, filingOnly, faqs)

      val button = document.getElementById("try-again")
      button.text mustBe "Try again"
      button.attr("href") mustBe "/paye-expenses-and-benefits/agent/try-again"
      href(document, "helpdesk") mustBe "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/online-services-helpdesk"
      document.getElementById("helpdesk").parent.text mustBe "If this keeps happening, contact the Online Services Helpdesk."
    }
  }

  "Agent placeholder pages" - {

    val fullNav = Seq(
      atAGlance,
      "Clients" -> "/paye-expenses-and-benefits/agent/clients",
      filingOnly,
      "Recent notices" -> "/notice-view/agent/AB1234/recent-notices",
      "Notice options" -> "/paye-expenses-and-benefits/agent/notice-options",
      "Notifications email" -> "/paye-expenses-and-benefits/agent/notification-email",
      faqs
    )

    def active(document: Document): String = document.select(".govuk-service-navigation__item--active a").text

    "must show the full navigation with the current page selected" in {
      val view     = application.injector.instanceOf[AgentPlaceholderView]
      val document = doc(view("agentPlaceholder.noticeOptions", agentReference, "/paye-expenses-and-benefits/agent/notice-options")(request, messages(application)))

      document.select("h1").text mustBe "Notice options"
      document.select(".govuk-service-navigation__service-name").text mustBe "Pay As You Earn"
      navItems(document) mustBe fullNav
      active(document) mustBe "Notice options"
    }

    "must show the full navigation with Clients selected on the clients placeholder" in {
      val view     = application.injector.instanceOf[AgentClientsPlaceholderView]
      val document = doc(view(None, agentReference)(request, messages(application)))

      navItems(document) mustBe fullNav
      active(document) mustBe "Clients"
    }
  }
}
