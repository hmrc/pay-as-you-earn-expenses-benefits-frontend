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

package models

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}

class PayeEnrolmentSpec extends AnyFreeSpec with Matchers {

  private def agentEnrolment(state: String = "Activated", identifiers: Seq[EnrolmentIdentifier] = Seq(EnrolmentIdentifier("IRAgentReference", "AB1234"))) =
    Enrolment("IR-PAYE-AGENT", identifiers, state)

  private def orgEnrolment(state: String = "Activated", identifiers: Seq[EnrolmentIdentifier] = Seq(
    EnrolmentIdentifier("TaxOfficeNumber", "123"),
    EnrolmentIdentifier("TaxOfficeReference", "AB45678")
  )) = Enrolment("IR-PAYE", identifiers, state)

  "PayeEnrolment.agentReference" - {

    "must return Activated with the IRAgentReference when the enrolment is activated" in {
      PayeEnrolment.agentReference(Enrolments(Set(agentEnrolment()))) mustBe EnrolmentCheck.Activated("AB1234")
    }

    "must return NotActivated when the enrolment is not activated" in {
      PayeEnrolment.agentReference(Enrolments(Set(agentEnrolment(state = "NotYetActivated")))) mustBe EnrolmentCheck.NotActivated
    }

    "must return Missing when there is no IR-PAYE-AGENT enrolment" in {
      PayeEnrolment.agentReference(Enrolments(Set(orgEnrolment()))) mustBe EnrolmentCheck.Missing
    }

    "must return Missing when the enrolment is activated but has no identifier" in {
      PayeEnrolment.agentReference(Enrolments(Set(agentEnrolment(identifiers = Nil)))) mustBe EnrolmentCheck.Missing
    }
  }

  "PayeEnrolment.empRef" - {

    "must return Activated with the EmpRef when the enrolment is activated" in {
      val result = PayeEnrolment.empRef(Enrolments(Set(orgEnrolment())))
      result mustBe EnrolmentCheck.Activated(EmpRef("123", "AB45678"))
    }

    "must format the EmpRef as TaxOfficeNumber/TaxOfficeReference" in {
      EmpRef("123", "AB45678").value mustBe "123/AB45678"
    }

    "must return NotActivated when the enrolment is not activated" in {
      PayeEnrolment.empRef(Enrolments(Set(orgEnrolment(state = "NotYetActivated")))) mustBe EnrolmentCheck.NotActivated
    }

    "must return Missing when there is no IR-PAYE enrolment" in {
      PayeEnrolment.empRef(Enrolments(Set(agentEnrolment()))) mustBe EnrolmentCheck.Missing
    }

    "must return Missing when only one of the two identifiers is present" in {
      val enrolment = orgEnrolment(identifiers = Seq(EnrolmentIdentifier("TaxOfficeNumber", "123")))
      PayeEnrolment.empRef(Enrolments(Set(enrolment))) mustBe EnrolmentCheck.Missing
    }
  }
}
