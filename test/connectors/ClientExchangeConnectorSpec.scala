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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import utils.WireMockHelper

class ClientExchangeConnectorSpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneAppPerSuite
    with WireMockHelper {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure("microservice.services.paye-exb.port" -> server.port())
      .build()

  private lazy val connector = app.injector.instanceOf[ClientExchangeConnector]

  private val path = "/paye-exb/clientlistexchange/PAYE/cred-id-1234/agent/clientlist"

  private val waitTimeXml =
    """<gwe:AsynchronousProcessWaitTime browserInterval="8000" xmlns:gwe="gwe"><BusinessServiceInterval>1000</BusinessServiceInterval></gwe:AsynchronousProcessWaitTime>"""

  "ClientExchangeConnector" - {

    "startClientListDownload" - {

      "must return true when the exchange returns 200" in {
        server.stubFor(
          get(urlPathEqualTo(path))
            .willReturn(aResponse().withStatus(OK).withHeader("Content-Type", "application/xml").withBody(waitTimeXml))
        )

        connector.startClientListDownload("cred-id-1234").futureValue mustBe true
        server.verify(getRequestedFor(urlPathEqualTo(path)))
      }

      "must return false when the exchange returns 400" in {
        server.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(BAD_REQUEST).withBody("Invalid ServiceId")))

        connector.startClientListDownload("cred-id-1234").futureValue mustBe false
      }

      "must return false when the exchange returns 500" in {
        server.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR).withBody("Server Error")))

        connector.startClientListDownload("cred-id-1234").futureValue mustBe false
      }

      "must fail when the exchange cannot be reached" in {
        server.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)))

        connector.startClientListDownload("cred-id-1234").failed.futureValue mustBe an[Exception]
      }
    }
  }
}
