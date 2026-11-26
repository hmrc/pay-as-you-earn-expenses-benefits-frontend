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

package viewmodels.agent

import config.FrontendAppConfig
import controllers.routes
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.servicenavigation.{ServiceNavigation, ServiceNavigationItem}

import javax.inject.Inject

class AgentNavigation @Inject()(config: FrontendAppConfig) {

  def full(agentReference: String, current: String = atAGlanceUrl)(implicit messages: Messages): ServiceNavigation =
    navigation(current, Seq(
      atAGlance,
      item("agent.nav.clients", controllers.atAGlance.routes.AgentClientsController.onPageLoad().url),
      filingOnlyService,
      item("agent.nav.recentNotices", config.recentNoticesUrl(agentReference)),
      item("agent.nav.noticeOptions", controllers.atAGlance.routes.AgentPlaceholderController.noticeOptions().url),
      item("agent.nav.notificationsEmail", controllers.atAGlance.routes.AgentPlaceholderController.notificationEmail().url),
      faqs
    ))

  def noClients(implicit messages: Messages): ServiceNavigation =
    navigation(atAGlanceUrl, Seq(atAGlance, item("agent.nav.requestAuthorisation", config.requestAuthorisationUrl), filingOnlyService, faqs))

  def limited(implicit messages: Messages): ServiceNavigation =
    navigation(atAGlanceUrl, Seq(atAGlance, filingOnlyService, faqs))

  private def atAGlanceUrl: String = controllers.atAGlance.routes.AgentAtAGlanceController.onPageLoad().url

  private def atAGlance(implicit messages: Messages): ServiceNavigationItem =
    item("agent.nav.atAGlance", atAGlanceUrl)

  private def filingOnlyService(implicit messages: Messages): ServiceNavigationItem =
    item("agent.nav.filingOnlyService", controllers.atAGlance.routes.AgentPlaceholderController.filingChoices().url)

  private def faqs(implicit messages: Messages): ServiceNavigationItem =
    item("agent.nav.faqs", config.payeFaqsUrl)

  private def item(key: String, href: String)(implicit messages: Messages): ServiceNavigationItem =
    ServiceNavigationItem(content = Text(messages(key)), href = href)

  private def navigation(current: String, items: Seq[ServiceNavigationItem])(implicit messages: Messages): ServiceNavigation =
    ServiceNavigation(
      serviceName = Some(messages("agent.serviceName")),
      serviceUrl  = Some(atAGlanceUrl),
      navigation  = items.map(i => if (i.href == current) i.copy(active = true, current = true) else i)
    )
}
