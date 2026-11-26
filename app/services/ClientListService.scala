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

package services

import config.FrontendAppConfig
import connectors.{ClientExchangeConnector, PayeExbConnector}
import models.UserAnswers
import models.clientlist.{ClientListResult, ClientListStatus, GetAllClientsRequest}
import pages.ClientListRequestedAtPage
import play.api.Logging
import repositories.SessionRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ClientListService @Inject()(
                                   payeExbConnector: PayeExbConnector,
                                   clientExchangeConnector: ClientExchangeConnector,
                                   sessionRepository: SessionRepository,
                                   config: FrontendAppConfig,
                                   clock: Clock
                                 )(implicit ec: ExecutionContext) extends Logging {

  def getClientList(userAnswers: UserAnswers, credentialId: String, agentReference: String)
                   (implicit hc: HeaderCarrier): Future[ClientListResult] =
    payeExbConnector.getClientListStatus(credentialId).flatMap {
      case ClientListStatus.InitiateDownload =>
        startDownload(userAnswers, credentialId).flatMap {
          case Some(updatedAnswers) =>
            payeExbConnector.getClientListStatus(credentialId).flatMap {
              case ClientListStatus.InitiateDownload => Future.successful(ClientListResult.InProgress)
              case status                            => handleStatus(status, updatedAnswers, credentialId, agentReference)
            }
          case None =>
            Future.successful(ClientListResult.Failed)
        }
      case status =>
        handleStatus(status, userAnswers, credentialId, agentReference)
    }

  def retryDownload(userAnswers: UserAnswers, credentialId: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    startDownload(userAnswers, credentialId).map(_.isDefined)

  private def handleStatus(status: ClientListStatus, userAnswers: UserAnswers, credentialId: String, agentReference: String)
                          (implicit hc: HeaderCarrier): Future[ClientListResult] =
    status match {
      case ClientListStatus.InProgress =>
        userAnswers.get(ClientListRequestedAtPage) match {
          case Some(requestedAt) if gracePeriodExpired(requestedAt) =>
            logger.warn("[ClientListService][getClientList] Client list download has passed the grace period")
            clear(userAnswers).map(_ => ClientListResult.Failed)
          case Some(_) =>
            Future.successful(ClientListResult.InProgress)
          case None =>
            save(userAnswers.set(ClientListRequestedAtPage, Instant.now(clock)).get).map(_ => ClientListResult.InProgress)
        }

      case ClientListStatus.Succeeded =>
        for {
          _        <- clear(userAnswers)
          response <- payeExbConnector.getAllClients(GetAllClientsRequest(agentReference, credentialId))
        } yield if (response.totalClients > 0) ClientListResult.HasClients(response) else ClientListResult.NoClients

      case ClientListStatus.Failed =>
        clear(userAnswers).map(_ => ClientListResult.Failed)

      case ClientListStatus.InitiateDownload =>
        Future.successful(ClientListResult.InProgress)
    }

  private def startDownload(userAnswers: UserAnswers, credentialId: String)(implicit hc: HeaderCarrier): Future[Option[UserAnswers]] =
    clientExchangeConnector.startClientListDownload(credentialId).flatMap {
      case true =>
        val updatedAnswers = userAnswers.set(ClientListRequestedAtPage, Instant.now(clock)).get
        save(updatedAnswers).map(_ => Some(updatedAnswers))
      case false =>
        Future.successful(None)
    }

  private def gracePeriodExpired(requestedAt: Instant): Boolean =
    Instant.now(clock).isAfter(requestedAt.plusSeconds(config.clientListGracePeriodSeconds))

  private def clear(userAnswers: UserAnswers): Future[Boolean] =
    userAnswers.get(ClientListRequestedAtPage) match {
      case Some(_) => save(userAnswers.remove(ClientListRequestedAtPage).get)
      case None    => Future.successful(true)
    }

  private def save(userAnswers: UserAnswers): Future[Boolean] =
    sessionRepository.set(userAnswers)
}
