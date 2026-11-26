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
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import utils.WireMockHelper

class ClientExchangeConnectorISpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneAppPerSuite
    with WireMockHelper {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.paye-exb.protocol" -> "http",
        "microservice.services.paye-exb.host"     -> "localhost",
        "microservice.services.paye-exb.port"     -> server.port(),
        "http-verbs.retries.enabled"              -> false
      )
      .build()

  private lazy val connector = app.injector.instanceOf[ClientExchangeConnector]

  private val credentialId = "cred-id-1234"

  private val exchangePath = s"/paye-exb/clientlistexchange/PAYE/$credentialId/agent/clientlist"

  private val waitTimeXml =
    """<gwe:AsynchronousProcessWaitTime browserInterval="8000" xmlns:gwe="gwe"><BusinessServiceInterval>1000</BusinessServiceInterval></gwe:AsynchronousProcessWaitTime>"""

  private def stubExchange(status: Int, body: String = ""): Unit =
    server.stubFor(
      get(urlPathEqualTo(exchangePath))
        .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/xml").withBody(body))
    )

  "ClientExchangeConnector Integration Tests" - {

    "startClientListDownload" - {

      "must return true when the exchange returns 200 with the wait time" in {
        stubExchange(200, waitTimeXml)

        connector.startClientListDownload(credentialId).futureValue mustBe true
      }

      "must make a single GET request to the exchange for the credential id" in {
        stubExchange(200, waitTimeXml)

        connector.startClientListDownload(credentialId).futureValue

        server.verify(1, getRequestedFor(urlPathEqualTo(exchangePath)))
        server.verify(0, postRequestedFor(anyUrl()))
      }

      Seq(400, 401, 403, 404, 500, 502, 503).foreach { status =>
        s"must return false when the exchange returns $status" in {
          stubExchange(status, "Error")

          connector.startClientListDownload(credentialId).futureValue mustBe false
        }
      }

      "must fail when the exchange cannot be reached" in {
        server.stubFor(get(urlPathEqualTo(exchangePath)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

        connector.startClientListDownload(credentialId).failed.futureValue mustBe a[Throwable]
      }
    }
  }
}
