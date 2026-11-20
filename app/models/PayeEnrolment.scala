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

import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}

final case class EmpRef(taxOfficeNumber: String, taxOfficeReference: String):
  def value: String = s"$taxOfficeNumber/$taxOfficeReference"

enum EnrolmentReference:
  case Agent(agentReference: String)
  case Organisation(empRef: EmpRef)

  def value: String = this match
    case Agent(agentReference) => agentReference
    case Organisation(empRef)  => empRef.value

enum EnrolmentCheck[+A]:
  case Activated(reference: A) extends EnrolmentCheck[A]
  case NotActivated            extends EnrolmentCheck[Nothing]
  case Missing                 extends EnrolmentCheck[Nothing]

  def isActivated: Boolean = this match
    case Activated(_) => true
    case _            => false

object PayeEnrolment:

  val AgentKey: String           = "IR-PAYE-AGENT"
  val AgentIdentifier: String    = "IRAgentReference"

  val OrgKey: String             = "IR-PAYE"
  val TaxOfficeNumber: String    = "TaxOfficeNumber"
  val TaxOfficeReference: String = "TaxOfficeReference"

  def agentReference(enrolments: Enrolments): EnrolmentCheck[String] =
    check(enrolments, AgentKey)(_.getIdentifier(AgentIdentifier).map(_.value))

  def empRef(enrolments: Enrolments): EnrolmentCheck[EmpRef] =
    check(enrolments, OrgKey) { enrolment =>
      for
        ton <- enrolment.getIdentifier(TaxOfficeNumber)
        tor <- enrolment.getIdentifier(TaxOfficeReference)
      yield EmpRef(ton.value, tor.value)
    }

  private def check[A](enrolments: Enrolments, key: String)(extract: Enrolment => Option[A]): EnrolmentCheck[A] =
    enrolments.getEnrolment(key) match
      case None                                      => EnrolmentCheck.Missing
      case Some(enrolment) if !enrolment.isActivated => EnrolmentCheck.NotActivated
      case Some(enrolment)                           =>
        extract(enrolment).fold[EnrolmentCheck[A]](EnrolmentCheck.Missing)(EnrolmentCheck.Activated(_))
