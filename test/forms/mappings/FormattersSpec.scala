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

package forms.mappings

import models.Enumerable
import org.scalatest.EitherValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.data.FormError

object FormattersSpec {

  sealed trait Colour
  case object Red  extends Colour
  case object Blue extends Colour

  object Colour {
    implicit val enumerable: Enumerable[Colour] =
      Enumerable(Seq(Red, Blue).map(v => v.toString -> v): _*)
  }
}

class FormattersSpec extends AnyFreeSpec with Matchers with EitherValues with Formatters {

  import FormattersSpec._

  private val fieldKey = "value"

  "stringFormatter" - {

    val formatter = stringFormatter("error.required")

    "must bind a non-empty string" in {
      formatter.bind(fieldKey, Map(fieldKey -> "foo")).value mustEqual "foo"
    }

    "must not bind when the fieldKey is missing" in {
      formatter.bind(fieldKey, Map.empty).left.value mustEqual Seq(FormError(fieldKey, "error.required"))
    }

    "must not bind a blank string" in {
      formatter.bind(fieldKey, Map(fieldKey -> "   ")).left.value mustEqual Seq(FormError(fieldKey, "error.required"))
    }

    "must unbind a string" in {
      formatter.unbind(fieldKey, "foo") mustEqual Map(fieldKey -> "foo")
    }
  }

  "booleanFormatter" - {

    val formatter = booleanFormatter("error.required", "error.boolean")

    "must bind true" in {
      formatter.bind(fieldKey, Map(fieldKey -> "true")).value mustEqual true
    }

    "must bind false" in {
      formatter.bind(fieldKey, Map(fieldKey -> "false")).value mustEqual false
    }

    "must not bind a non-boolean value" in {
      formatter.bind(fieldKey, Map(fieldKey -> "maybe")).left.value mustEqual Seq(FormError(fieldKey, "error.boolean"))
    }

    "must not bind when the fieldKey is missing" in {
      formatter.bind(fieldKey, Map.empty).left.value mustEqual Seq(FormError(fieldKey, "error.required"))
    }

    "must unbind a boolean" in {
      formatter.unbind(fieldKey, true) mustEqual Map(fieldKey -> "true")
    }
  }

  "intFormatter" - {

    val formatter = intFormatter("error.required", "error.wholeNumber", "error.nonNumeric")

    "must bind a whole number" in {
      formatter.bind(fieldKey, Map(fieldKey -> "123")).value mustEqual 123
    }

    "must bind a whole number containing commas" in {
      formatter.bind(fieldKey, Map(fieldKey -> "1,234")).value mustEqual 1234
    }

    "must not bind a decimal" in {
      formatter.bind(fieldKey, Map(fieldKey -> "1.5")).left.value mustEqual Seq(FormError(fieldKey, "error.wholeNumber"))
    }

    "must not bind a non-numeric value" in {
      formatter.bind(fieldKey, Map(fieldKey -> "abc")).left.value mustEqual Seq(FormError(fieldKey, "error.nonNumeric"))
    }

    "must not bind when the fieldKey is missing" in {
      formatter.bind(fieldKey, Map.empty).left.value mustEqual Seq(FormError(fieldKey, "error.required"))
    }

    "must unbind an int" in {
      formatter.unbind(fieldKey, 123) mustEqual Map(fieldKey -> "123")
    }
  }

  "enumerableFormatter" - {

    val formatter = enumerableFormatter[Colour]("error.required", "error.invalid")

    "must bind a valid value" in {
      formatter.bind(fieldKey, Map(fieldKey -> "Red")).value mustEqual Red
    }

    "must not bind an invalid value" in {
      formatter.bind(fieldKey, Map(fieldKey -> "Green")).left.value mustEqual Seq(FormError(fieldKey, "error.invalid"))
    }

    "must not bind when the fieldKey is missing" in {
      formatter.bind(fieldKey, Map.empty).left.value mustEqual Seq(FormError(fieldKey, "error.required"))
    }

    "must unbind a value" in {
      formatter.unbind(fieldKey, Blue) mustEqual Map(fieldKey -> "Blue")
    }
  }

  "currencyFormatter" - {

    val formatter = currencyFormatter("error.required", "error.invalidNumeric", "error.nonNumeric")

    "must bind a whole amount" in {
      formatter.bind(fieldKey, Map(fieldKey -> "100")).value mustEqual BigDecimal(100)
    }

    "must bind an amount with pence" in {
      formatter.bind(fieldKey, Map(fieldKey -> "100.50")).value mustEqual BigDecimal("100.50")
    }

    "must bind an amount with a pound sign, commas and spaces" in {
      formatter.bind(fieldKey, Map(fieldKey -> "£1,000 .5")).value mustEqual BigDecimal("1000.5")
    }

    "must not bind an amount with more than two decimal places" in {
      formatter.bind(fieldKey, Map(fieldKey -> "1.234")).left.value mustEqual Seq(FormError(fieldKey, "error.invalidNumeric"))
    }

    "must not bind a non-numeric value" in {
      formatter.bind(fieldKey, Map(fieldKey -> "abc")).left.value mustEqual Seq(FormError(fieldKey, "error.nonNumeric"))
    }

    "must not bind a value that matches the pattern but is not a number" in {
      formatter.bind(fieldKey, Map(fieldKey -> "£")).left.value mustEqual Seq(FormError(fieldKey, "error.nonNumeric"))
    }

    "must not bind when the fieldKey is missing" in {
      formatter.bind(fieldKey, Map.empty).left.value mustEqual Seq(FormError(fieldKey, "error.required"))
    }

    "must unbind an amount" in {
      formatter.unbind(fieldKey, BigDecimal("12.34")) mustEqual Map(fieldKey -> "12.34")
    }
  }
}
