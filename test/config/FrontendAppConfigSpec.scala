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

package config

import base.SpecBase

class FrontendAppConfigSpec extends SpecBase {

  "FrontendAppConfig" - {

    "must build the paye-exb base url from config" in {
      val application = applicationBuilder().build()
      val appConfig   = application.injector.instanceOf[FrontendAppConfig]

      appConfig.payeExbUrl mustBe "http://localhost:20114/paye-exb"
    }

    "must build the external agent links from config" in {
      val application = applicationBuilder().build()
      val appConfig   = application.injector.instanceOf[FrontendAppConfig]

      appConfig.recentNoticesUrl("AB1234") mustBe "/notice-view/agent/AB1234/recent-notices"
      appConfig.filingOnlyReturnUrl("AB1234") mustBe "/paye-file/agent/AB1234"
      appConfig.payeFaqsUrl mustBe "/information/faqs/paye"
      appConfig.payrollSoftwareUrl mustBe "https://www.gov.uk/payroll-software"
      appConfig.onlineServicesHelpdeskUrl mustBe "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/online-services-helpdesk"
      appConfig.requestAuthorisationUrl mustBe "https://www.gov.uk/guidance/client-authorisation-an-overview"
    }

    "must read the client list settings from config" in {
      val application = applicationBuilder().build()
      val appConfig   = application.injector.instanceOf[FrontendAppConfig]

      appConfig.clientListService mustBe "PAYE"
      appConfig.clientListGracePeriodSeconds mustBe 14400
    }
  }
}
