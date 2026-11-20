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

import com.google.inject.Inject
import config.FrontendAppConfig
import controllers.routes
import models.{EnrolmentCheck, EnrolmentReference, PayeEnrolment}
import models.requests.IdentifierRequest
import play.api.Logging
import play.api.mvc.*
import play.api.mvc.Results.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

trait AgentIdentifierAction
  extends ActionBuilder[IdentifierRequest, AnyContent]
    with ActionFunction[Request, IdentifierRequest]

class AuthenticatedAgentIdentifierAction @Inject()(
                                                    override val authConnector: AuthConnector,
                                                    config: FrontendAppConfig,
                                                    val parser: BodyParsers.Default
                                                  )(implicit val executionContext: ExecutionContext)
  extends AgentIdentifierAction with AuthorisedFunctions with Logging {

  private val retrievals = Retrievals.internalId and Retrievals.affinityGroup and Retrievals.allEnrolments

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised(AuthProviders(GovernmentGateway)).retrieve(retrievals) {
      case None ~ _ ~ _ =>
        Future.failed(new UnauthorizedException("Unable to retrieve internal Id"))

      case Some(internalId) ~ Some(AffinityGroup.Agent) ~ enrolments =>
        PayeEnrolment.agentReference(enrolments) match {
          case EnrolmentCheck.Activated(agentReference) =>
            block(IdentifierRequest(request, internalId, EnrolmentReference.Agent(agentReference)))
          case EnrolmentCheck.NotActivated =>
            logger.info(s"[AgentIdentifierAction] Agent has a ${PayeEnrolment.AgentKey} enrolment but it is not activated")
            Future.successful(Redirect(routes.ActivateEnrolmentController.onPageLoad()))
          case EnrolmentCheck.Missing =>
            logger.warn(s"[AgentIdentifierAction] Agent has no usable ${PayeEnrolment.AgentKey} enrolment")
            Future.successful(Redirect(routes.UnauthorisedController.onPageLoad()))
        }

      case _ ~ Some(AffinityGroup.Organisation) ~ enrolments if PayeEnrolment.empRef(enrolments).isActivated =>
        logger.info("[AgentIdentifierAction] Organisation with an activated IR-PAYE enrolment, redirecting to ORG1")
        Future.successful(Redirect(routes.Org1Controller.onPageLoad()))

      case _ ~ affinityGroup ~ _ =>
        logger.warn(s"[AgentIdentifierAction] Non-agent user tried to access an agent page, affinity group: $affinityGroup")
        Future.successful(Redirect(routes.UnauthorisedController.onPageLoad()))
    } recover {
      case _: NoActiveSession =>
        Redirect(config.loginUrl, Map("continue" -> Seq(s"${config.host}${request.uri}")))
      case _: AuthorisationException =>
        Redirect(routes.UnauthorisedController.onPageLoad())
    }
  }
}
