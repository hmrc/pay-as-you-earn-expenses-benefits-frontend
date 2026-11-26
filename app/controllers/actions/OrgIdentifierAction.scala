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

trait OrgIdentifierAction
  extends ActionBuilder[IdentifierRequest, AnyContent]
    with ActionFunction[Request, IdentifierRequest]

class AuthenticatedOrgIdentifierAction @Inject()(
                                                  override val authConnector: AuthConnector,
                                                  config: FrontendAppConfig,
                                                  val parser: BodyParsers.Default
                                                )(implicit val executionContext: ExecutionContext)
  extends OrgIdentifierAction with AuthorisedFunctions with Logging {

  private val retrievals = Retrievals.internalId and Retrievals.credentials and Retrievals.affinityGroup and Retrievals.allEnrolments

  override def invokeBlock[A](request: Request[A], block: IdentifierRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised(AuthProviders(GovernmentGateway)).retrieve(retrievals) {
      case None ~ _ ~ _ ~ _ =>
        Future.failed(new UnauthorizedException("Unable to retrieve internal Id"))

      case _ ~ None ~ _ ~ _ =>
        Future.failed(new UnauthorizedException("Unable to retrieve credentials"))

      case Some(internalId) ~ Some(credentials) ~ Some(AffinityGroup.Organisation) ~ enrolments =>
        PayeEnrolment.empRef(enrolments) match {
          case EnrolmentCheck.Activated(empRef) =>
            block(IdentifierRequest(request, internalId, EnrolmentReference.Organisation(empRef), credentials.providerId))
          case EnrolmentCheck.NotActivated =>
            logger.info(s"[OrgIdentifierAction] Organisation has a ${PayeEnrolment.OrgKey} enrolment but it is not activated")
            Future.successful(Redirect(routes.ActivateEnrolmentController.onPageLoad()))
          case EnrolmentCheck.Missing =>
            logger.warn(s"[OrgIdentifierAction] Organisation has no usable ${PayeEnrolment.OrgKey} enrolment")
            Future.successful(Redirect(routes.UnauthorisedController.onPageLoad()))
        }

      case _ ~ _ ~ Some(AffinityGroup.Agent) ~ enrolments if PayeEnrolment.agentReference(enrolments).isActivated =>
        logger.info("[OrgIdentifierAction] Agent with an activated IR-PAYE-AGENT enrolment, redirecting to the agent at a glance page")
        Future.successful(Redirect(controllers.atAGlance.routes.AgentAtAGlanceController.onPageLoad()))

      case _ ~ _ ~ affinityGroup ~ _ =>
        logger.warn(s"[OrgIdentifierAction] Non-organisation user tried to access an organisation page, affinity group: $affinityGroup")
        Future.successful(Redirect(routes.UnauthorisedController.onPageLoad()))
    } recover {
      case _: NoActiveSession =>
        Redirect(config.loginUrl, Map("continue" -> Seq(s"${config.host}${request.uri}")))
      case _: AuthorisationException =>
        Redirect(routes.UnauthorisedController.onPageLoad())
    }
  }
}
