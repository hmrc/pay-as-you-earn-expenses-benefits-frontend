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

import models.Enumerable

enum SearchType(override val toString: String):
  case EmployerName    extends SearchType("employerName")
  case EmployerPaye    extends SearchType("employerPAYE")
  case ClientReference extends SearchType("clientReference")

object SearchType extends Enumerable.Implicits:

  given enumerable: Enumerable[SearchType] = Enumerable(values.toSeq.map(v => v.toString -> v)*)
