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

import com.fasterxml.jackson.core.JsonParseException
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.Fault
import models.clientlist.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, JsValidationException, UpstreamErrorResponse}
import utils.WireMockHelper

class PayeExbConnectorISpec
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

  private lazy val connector = app.injector.instanceOf[PayeExbConnector]

  private val statusPath  = "/paye-exb/client-list/status"
  private val clientsPath = "/paye-exb/clients/all"

  private val credentialId = "cred-id-1234"

  private val statusRequestBody = Json.obj("credentialId" -> credentialId, "service" -> "PAYE", "gracePeriod" -> 14400).toString

  private val clientsRequest = GetAllClientsRequest("AB1234", credentialId)

  private val client = PayeClient(1L, "Acme Ltd", Some("Trading"), 123, "123/AB45678", "123PA00123456", Some("REF1"), true)

  private def stubPost(path: String, status: Int, body: String): Unit =
    server.stubFor(
      post(urlPathEqualTo(path))
        .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body))
    )

  "PayeExbConnector Integration Tests" - {

    "getClientListStatus" - {

      "must return each status returned by paye-exb" in {
        Seq(
          -1 -> ClientListStatus.InitiateDownload,
          0  -> ClientListStatus.InProgress,
          1  -> ClientListStatus.Succeeded,
          2  -> ClientListStatus.Failed
        ).foreach { case (code, expected) =>
          stubPost(statusPath, 200, Json.obj("status" -> code).toString)
          connector.getClientListStatus(credentialId).futureValue mustBe expected
        }
      }

      "must send the credential id, service and grace period as JSON" in {
        stubPost(statusPath, 200, Json.obj("status" -> 1).toString)

        connector.getClientListStatus(credentialId).futureValue

        server.verify(
          1,
          postRequestedFor(urlPathEqualTo(statusPath))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(equalToJson(statusRequestBody))
        )
      }

      Seq(400, 401, 403, 404, 500, 502, 503).foreach { status =>
        s"must fail with an UpstreamErrorResponse when paye-exb returns $status" in {
          stubPost(statusPath, status, "")

          val result = connector.getClientListStatus(credentialId).failed.futureValue

          result mustBe an[UpstreamErrorResponse]
          result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe status
        }
      }

      "must fail when paye-exb returns an unknown status" in {
        stubPost(statusPath, 200, Json.obj("status" -> 9).toString)

        connector.getClientListStatus(credentialId).failed.futureValue mustBe a[JsValidationException]
      }

      "must fail when paye-exb returns malformed JSON" in {
        stubPost(statusPath, 200, "{invalid json}")

        connector.getClientListStatus(credentialId).failed.futureValue mustBe a[JsonParseException]
      }

      "must fail when paye-exb cannot be reached" in {
        server.stubFor(post(urlPathEqualTo(statusPath)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

        connector.getClientListStatus(credentialId).failed.futureValue mustBe a[Throwable]
      }
    }

    "getAllClients" - {

      "must return the clients returned by paye-exb" in {
        val response = GetAllClientsResponse(1, Seq(client), Seq("A"))
        stubPost(clientsPath, 200, Json.toJson(response).toString)

        connector.getAllClients(clientsRequest).futureValue mustBe response
      }

      "must return an empty response when the agent has no clients" in {
        stubPost(clientsPath, 200, Json.obj("totalClients" -> 0, "clients" -> Json.arr(), "clientNameChars" -> Json.arr()).toString)

        connector.getAllClients(clientsRequest).futureValue mustBe GetAllClientsResponse(0, Nil, Nil)
      }

      "must send the agent reference and credential id as JSON" in {
        stubPost(clientsPath, 200, Json.toJson(GetAllClientsResponse(0, Nil, Nil)).toString)

        connector.getAllClients(clientsRequest).futureValue

        server.verify(
          1,
          postRequestedFor(urlPathEqualTo(clientsPath))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(equalToJson(Json.toJson(clientsRequest).toString))
        )
      }

      Seq(400, 401, 403, 404, 500, 502, 503).foreach { status =>
        s"must fail with an UpstreamErrorResponse when paye-exb returns $status" in {
          stubPost(clientsPath, status, "")

          val result = connector.getAllClients(clientsRequest).failed.futureValue

          result mustBe an[UpstreamErrorResponse]
          result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe status
        }
      }

      "must fail when paye-exb returns JSON with missing fields" in {
        stubPost(clientsPath, 200, Json.obj("totalClients" -> 1).toString)

        connector.getAllClients(clientsRequest).failed.futureValue mustBe a[JsValidationException]
      }

      "must fail when paye-exb returns malformed JSON" in {
        stubPost(clientsPath, 200, "{invalid json}")

        connector.getAllClients(clientsRequest).failed.futureValue mustBe a[JsonParseException]
      }

      "must fail when paye-exb cannot be reached" in {
        server.stubFor(post(urlPathEqualTo(clientsPath)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

        connector.getAllClients(clientsRequest).failed.futureValue mustBe a[Throwable]
      }
    }
  }
}
