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
import models.clientlist.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import utils.WireMockHelper

class PayeExbConnectorSpec
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

  private lazy val connector = app.injector.instanceOf[PayeExbConnector]

  private val statusPath  = "/paye-exb/client-list/status"
  private val clientsPath = "/paye-exb/clients/all"

  private def stubPost(path: String, status: Int, body: String) =
    server.stubFor(
      post(urlPathEqualTo(path))
        .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body))
    )

  private val client = PayeClient(1L, "Acme Ltd", None, 123, "123/AB45678", "123PA00123456", Some("REF1"), true)

  "PayeExbConnector" - {

    "getClientListStatus" - {

      "must send the credential id, service and grace period" in {
        stubPost(statusPath, OK, Json.obj("status" -> 1).toString)

        connector.getClientListStatus("cred-id-1234").futureValue

        server.verify(
          postRequestedFor(urlPathEqualTo(statusPath))
            .withRequestBody(equalToJson(Json.obj("credentialId" -> "cred-id-1234", "service" -> "PAYE", "gracePeriod" -> 14400).toString))
        )
      }

      "must return each status returned by paye-exb" in {
        Seq(
          -1 -> ClientListStatus.InitiateDownload,
          0  -> ClientListStatus.InProgress,
          1  -> ClientListStatus.Succeeded,
          2  -> ClientListStatus.Failed
        ).foreach { case (code, expected) =>
          stubPost(statusPath, OK, Json.obj("status" -> code).toString)
          connector.getClientListStatus("cred-id-1234").futureValue mustBe expected
        }
      }

      "must fail with an UpstreamErrorResponse when paye-exb returns 500" in {
        stubPost(statusPath, INTERNAL_SERVER_ERROR, "")

        val result = connector.getClientListStatus("cred-id-1234").failed.futureValue
        result mustBe an[UpstreamErrorResponse]
        result.asInstanceOf[UpstreamErrorResponse].statusCode mustBe INTERNAL_SERVER_ERROR
      }

      "must fail when paye-exb returns an unknown status" in {
        stubPost(statusPath, OK, Json.obj("status" -> 9).toString)

        connector.getClientListStatus("cred-id-1234").failed.futureValue mustBe an[Exception]
      }
    }

    "getAllClients" - {

      val request = GetAllClientsRequest("AB1234", "cred-id-1234")

      "must send the request and return the clients" in {
        stubPost(clientsPath, OK, Json.toJson(GetAllClientsResponse(1, Seq(client), Seq("A"))).toString)

        connector.getAllClients(request).futureValue mustBe GetAllClientsResponse(1, Seq(client), Seq("A"))

        server.verify(
          postRequestedFor(urlPathEqualTo(clientsPath))
            .withRequestBody(equalToJson(Json.toJson(request).toString))
        )
      }

      "must return an empty response when there are no clients" in {
        stubPost(clientsPath, OK, Json.obj("totalClients" -> 0, "clients" -> Json.arr(), "clientNameChars" -> Json.arr()).toString)

        connector.getAllClients(request).futureValue mustBe GetAllClientsResponse(0, Nil, Nil)
      }

      "must fail with an UpstreamErrorResponse when paye-exb returns 404" in {
        stubPost(clientsPath, NOT_FOUND, "")

        connector.getAllClients(request).failed.futureValue mustBe an[UpstreamErrorResponse]
      }

      "must fail when paye-exb returns invalid json" in {
        stubPost(clientsPath, OK, Json.obj("unexpected" -> true).toString)

        connector.getAllClients(request).failed.futureValue mustBe an[Exception]
      }
    }
  }
}
