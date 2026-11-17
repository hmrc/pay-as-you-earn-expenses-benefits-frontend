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

package controllers.actions

import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class FakeSuccessfulAuthConnector(
                                   internalId: Option[String],
                                   affinityGroup: Option[AffinityGroup],
                                   enrolments: Enrolments
                                 ) extends AuthConnector {

  val serviceUrl: String = ""

  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
    Future.successful((new ~(new ~(internalId, affinityGroup), enrolments)).asInstanceOf[A])
}

object TestEnrolments {

  val agentReference: String = "AB1234"

  def payeAgent(state: String = "Activated"): Enrolment =
    Enrolment("IR-PAYE-AGENT", Seq(EnrolmentIdentifier("IRAgentReference", agentReference)), state)

  def payeOrg(state: String = "Activated"): Enrolment =
    Enrolment(
      "IR-PAYE",
      Seq(EnrolmentIdentifier("TaxOfficeNumber", "123"), EnrolmentIdentifier("TaxOfficeReference", "AB45678")),
      state
    )
}
