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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.*

class ClientListModelsSpec extends AnyFreeSpec with Matchers {

  private val client = PayeClient(
    instanceId        = 1L,
    employerName1     = "Acme Ltd",
    employerName2     = Some("Trading as Acme"),
    districtNumber    = 123,
    employerRef       = "123/AB45678",
    aoReference       = "123PA00123456",
    agentOwnRef       = Some("REF1"),
    previousYearFiled = true
  )

  "ClientListStatusRequest" - {

    "must write to json" in {
      Json.toJson(ClientListStatusRequest("cred-id-1234", "PAYE", 14400)) mustBe Json.obj(
        "credentialId" -> "cred-id-1234",
        "service"      -> "PAYE",
        "gracePeriod"  -> 14400
      )
    }
  }

  "ClientListStatus" - {

    "must read every known status" in {
      Json.obj("status" -> -1).as[ClientListStatus] mustBe ClientListStatus.InitiateDownload
      Json.obj("status" -> 0).as[ClientListStatus] mustBe ClientListStatus.InProgress
      Json.obj("status" -> 1).as[ClientListStatus] mustBe ClientListStatus.Succeeded
      Json.obj("status" -> 2).as[ClientListStatus] mustBe ClientListStatus.Failed
    }

    "must fail to read an unknown status" in {
      Json.obj("status" -> 9).validate[ClientListStatus] mustBe a[JsError]
    }

    "must fail to read when status is missing" in {
      Json.obj().validate[ClientListStatus] mustBe a[JsError]
    }

    "must find a status from its code" in {
      ClientListStatus.fromCode(1) mustBe Some(ClientListStatus.Succeeded)
      ClientListStatus.fromCode(5) mustBe None
    }
  }

  "GetAllClientsRequest" - {

    "must write to json with the default paging values" in {
      Json.toJson(GetAllClientsRequest("AB1234", "cred-id-1234")) mustBe Json.obj(
        "irAgentId"    -> "AB1234",
        "credentialId" -> "cred-id-1234",
        "start"        -> 0,
        "count"        -> 30,
        "sort"         -> 0,
        "order"        -> "ASC"
      )
    }
  }

  "GetAllClientsResponse" - {

    "must read a response with clients" in {
      val json = Json.obj(
        "totalClients" -> 1,
        "clients" -> Json.arr(Json.obj(
          "instanceId"        -> 1,
          "employerName1"     -> "Acme Ltd",
          "employerName2"     -> "Trading as Acme",
          "districtNumber"    -> 123,
          "employerRef"       -> "123/AB45678",
          "aoReference"       -> "123PA00123456",
          "agentOwnRef"       -> "REF1",
          "previousYearFiled" -> true
        )),
        "clientNameChars" -> Json.arr("A")
      )

      json.as[GetAllClientsResponse] mustBe GetAllClientsResponse(1, Seq(client), Seq("A"))
    }

    "must read a client without the optional fields" in {
      val json = Json.obj(
        "instanceId"        -> 2,
        "employerName1"     -> "Beta Ltd",
        "districtNumber"    -> 456,
        "employerRef"       -> "456/CD123",
        "aoReference"       -> "456PA00654321",
        "previousYearFiled" -> false
      )

      json.as[PayeClient] mustBe PayeClient(2L, "Beta Ltd", None, 456, "456/CD123", "456PA00654321", None, false)
    }

    "must read a response with no clients" in {
      Json.obj("totalClients" -> 0, "clients" -> Json.arr(), "clientNameChars" -> Json.arr())
        .as[GetAllClientsResponse] mustBe GetAllClientsResponse(0, Nil, Nil)
    }

    "must round trip" in {
      val response = GetAllClientsResponse(1, Seq(client), Seq("A"))
      Json.toJson(response).as[GetAllClientsResponse] mustBe response
    }
  }
}
