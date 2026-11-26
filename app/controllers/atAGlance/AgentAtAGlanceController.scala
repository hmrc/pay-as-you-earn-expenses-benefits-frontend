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

package controllers.atAGlance

import controllers.actions.{AgentIdentifierAction, DataRetrievalAction}
import forms.ClientSearchFormProvider
import models.UserAnswers
import models.clientlist.{ClientListResult, ClientSearch}
import models.requests.OptionalDataRequest
import pages.ClientSearchPage
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import repositories.SessionRepository
import services.ClientListService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.agent.*

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AgentAtAGlanceController @Inject()(
                                          val controllerComponents: MessagesControllerComponents,
                                          identify: AgentIdentifierAction,
                                          getData: DataRetrievalAction,
                                          sessionRepository: SessionRepository,
                                          clientListService: ClientListService,
                                          formProvider: ClientSearchFormProvider,
                                          clientsView: AgentClientsView,
                                          noClientsView: AgentNoClientsView,
                                          inProgressView: AgentClientListInProgressView,
                                          failedView: AgentClientListFailedView
                                        )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport {

  private val form: Form[ClientSearch] = formProvider()

  def onPageLoad(): Action[AnyContent] = (identify andThen getData).async { implicit request =>
    val userAnswers = request.userAnswers.getOrElse(UserAnswers(request.userId))
    val saved = request.userAnswers.fold(sessionRepository.set(userAnswers))(_ => Future.successful(true))
    val filledForm = userAnswers.get(ClientSearchPage).fold(form)(form.fill)

    saved.flatMap(_ => showClientList(userAnswers, filledForm, Ok))
  }

  def onSubmit(): Action[AnyContent] = (identify andThen getData).async { implicit request =>
    val userAnswers = request.userAnswers.getOrElse(UserAnswers(request.userId))

    form.bindFromRequest().fold(
      formWithErrors =>
        showClientList(userAnswers, formWithErrors, BadRequest),
      search =>
        for {
          updatedAnswers <- Future.fromTry(userAnswers.set(ClientSearchPage, search))
          _              <- sessionRepository.set(updatedAnswers)
        } yield Redirect(controllers.atAGlance.routes.AgentClientsController.onPageLoad())
    )
  }

  def tryAgain(): Action[AnyContent] = (identify andThen getData).async { implicit request =>
    val userAnswers = request.userAnswers.getOrElse(UserAnswers(request.userId))

    clientListService.retryDownload(userAnswers, request.credentialId).map {
      case true  => Redirect(controllers.atAGlance.routes.AgentAtAGlanceController.onPageLoad())
      case false => Ok(failedView(request.enrolmentReference.value))
    }
  }

  private def showClientList(userAnswers: UserAnswers, form: Form[ClientSearch], status: Status)
                            (implicit request: OptionalDataRequest[AnyContent]): Future[Result] = {
    val agentReference = request.enrolmentReference.value

    clientListService.getClientList(userAnswers, request.credentialId, agentReference).map {
      case ClientListResult.HasClients(_)        => status(clientsView(form, agentReference))
      case ClientListResult.NoClients            => Ok(noClientsView(agentReference))
      case ClientListResult.InProgress           => Ok(inProgressView(agentReference))
      case ClientListResult.Failed               => Ok(failedView(agentReference))
    }
  }
}
