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

import play.api.libs.json.{Json, OFormat}

final case class ClientSearch(employerName: Option[String], employerPAYE: Option[String], clientReference: Option[String]):

  def searchType: Option[SearchType] =
    (employerName, employerPAYE, clientReference) match
      case (Some(_), _, _) => Some(SearchType.EmployerName)
      case (_, Some(_), _) => Some(SearchType.EmployerPaye)
      case (_, _, Some(_)) => Some(SearchType.ClientReference)
      case _               => None

object ClientSearch:

  def apply(searchType: SearchType, value: String): ClientSearch = searchType match
    case SearchType.EmployerName    => ClientSearch(Some(value), None, None)
    case SearchType.EmployerPaye    => ClientSearch(None, Some(value), None)
    case SearchType.ClientReference => ClientSearch(None, None, Some(value))

  given OFormat[ClientSearch] = Json.format[ClientSearch]
