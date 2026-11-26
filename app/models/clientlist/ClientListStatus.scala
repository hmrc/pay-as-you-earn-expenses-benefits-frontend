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

import play.api.libs.json.*

enum ClientListStatus(val code: Int):
  case InitiateDownload extends ClientListStatus(-1)
  case InProgress       extends ClientListStatus(0)
  case Succeeded        extends ClientListStatus(1)
  case Failed           extends ClientListStatus(2)

object ClientListStatus:

  def fromCode(code: Int): Option[ClientListStatus] = values.find(_.code == code)

  given Reads[ClientListStatus] = (__ \ "status").read[Int].flatMap { code =>
    fromCode(code).fold(Reads.failed[ClientListStatus](s"Unknown client list status: $code"))(Reads.pure(_))
  }
