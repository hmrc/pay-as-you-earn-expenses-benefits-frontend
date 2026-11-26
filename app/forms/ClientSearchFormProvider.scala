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

package forms

import forms.mappings.Mappings
import models.clientlist.{ClientSearch, SearchType}
import play.api.data.Forms.{of, tuple}
import play.api.data.format.Formatter
import play.api.data.{Form, FormError}

import javax.inject.Inject

class ClientSearchFormProvider @Inject() extends Mappings {

  val employerNameMaxLength: Int    = 28
  val clientReferenceMaxLength: Int = 20
  val employerPayeRegex: String     = "^[0-9]{3}/[a-zA-Z0-9]{1,10}$"

  def apply(): Form[ClientSearch] =
    Form(
      tuple(
        "searchType"  -> enumerable[SearchType]("clientSearch.error.searchType.required", "clientSearch.error.searchType.required"),
        "searchValue" -> of(searchValueFormatter)
      ).transform[ClientSearch](
        { case (searchType, value) => ClientSearch(searchType, value) },
        search => (search.searchType.getOrElse(SearchType.EmployerName), Seq(search.employerName, search.employerPAYE, search.clientReference).flatten.headOption.getOrElse(""))
      )
    )

  private val searchValueFormatter: Formatter[String] = new Formatter[String] {

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] =
      data.get("searchType").flatMap(SearchType.enumerable.withName) match {
        case None => Right("")
        case Some(searchType) =>
          data.get(key).map(_.trim).filter(_.nonEmpty) match {
            case None        => Left(Seq(FormError(key, requiredKey(searchType))))
            case Some(value) => invalidKey(searchType, value).fold(Right(value))(error => Left(Seq(FormError(key, error))))
          }
      }

    override def unbind(key: String, value: String): Map[String, String] =
      Map(key -> value)
  }

  private def requiredKey(searchType: SearchType): String = searchType match {
    case SearchType.EmployerName    => "clientSearch.employerName.error.required"
    case SearchType.EmployerPaye    => "clientSearch.employerPAYE.error.required"
    case SearchType.ClientReference => "clientSearch.clientReference.error.required"
  }

  private def invalidKey(searchType: SearchType, value: String): Option[String] = searchType match {
    case SearchType.EmployerName    => Option.when(value.length > employerNameMaxLength)("clientSearch.employerName.error.length")
    case SearchType.EmployerPaye    => Option.when(!value.matches(employerPayeRegex))("clientSearch.employerPAYE.error.format")
    case SearchType.ClientReference => Option.when(value.length > clientReferenceMaxLength)("clientSearch.clientReference.error.length")
  }
}
