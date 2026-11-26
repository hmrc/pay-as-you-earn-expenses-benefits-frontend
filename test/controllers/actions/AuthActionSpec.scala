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

import base.SpecBase
import com.google.inject.Inject
import config.FrontendAppConfig
import controllers.routes
import play.api.mvc.{Action, AnyContent, BodyParsers, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.http.{HeaderCarrier, UnauthorizedException}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AuthActionSpec extends SpecBase {

  class Harness(authAction: IdentifierAction) {
    def onPageLoad(): Action[AnyContent] = authAction { _ => Results.Ok }
    def onPageLoadShowingReference(): Action[AnyContent] = authAction { request => Results.Ok(request.enrolmentReference.value) }
    def onPageLoadShowingCredentialId(): Action[AnyContent] = authAction { request => Results.Ok(request.credentialId) }
  }

  "Auth Action" - {

    "when the user hasn't logged in" - {

      "must redirect the user to log in " in {

        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig   = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new MissingBearerToken), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value must startWith(appConfig.loginUrl)
        }
      }
    }

    "the user's session has expired" - {

      "must redirect the user to log in " in {

        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig   = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new BearerTokenExpired), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value must startWith(appConfig.loginUrl)
        }
      }
    }

    "the user doesn't have sufficient enrolments" - {

      "must redirect the user to the unauthorised page" in {

        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig   = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new InsufficientEnrolments), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }
    }

    "the user doesn't have sufficient confidence level" - {

      "must redirect the user to the unauthorised page" in {

        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig   = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new InsufficientConfidenceLevel), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }
    }

    "the user used an unaccepted auth provider" - {

      "must redirect the user to the unauthorised page" in {

        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig   = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new UnsupportedAuthProvider), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }
    }

    "the user has an unsupported affinity group" - {

      "must redirect the user to the unauthorised page" in {

        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig   = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new UnsupportedAffinityGroup), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad().url)
        }
      }
    }

    "the user has an unsupported credential role" - {

      "must redirect the user to the unauthorised page" in {

        val application = applicationBuilder(userAnswers = None).build()

        running(application) {
          val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
          val appConfig   = application.injector.instanceOf[FrontendAppConfig]

          val authAction = new AuthenticatedIdentifierAction(new FakeFailingAuthConnector(new UnsupportedCredentialRole), appConfig, bodyParsers)
          val controller = new Harness(authAction)
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(routes.UnauthorisedController.onPageLoad().url)
        }
      }
    }

    "when the user is signed in" - {

      def runWith(enrolments: Enrolments, internalId: Option[String] = Some("id"), credentials: Option[Credentials] = Some(TestEnrolments.credentials)) = {
        val application = applicationBuilder(userAnswers = None).build()
        val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
        val appConfig   = application.injector.instanceOf[FrontendAppConfig]
        val authAction  = new AuthenticatedIdentifierAction(new FakeIdAndEnrolmentsAuthConnector(internalId, enrolments, credentials), appConfig, bodyParsers)
        (application, new Harness(authAction))
      }

      "must allow an activated agent and add the agent reference to the request" in {
        val (application, controller) = runWith(Enrolments(Set(TestEnrolments.payeAgent())))

        running(application) {
          val result = controller.onPageLoadShowingReference()(FakeRequest())

          status(result) mustBe OK
          contentAsString(result) mustBe "AB1234"
        }
      }

      "must allow an activated organisation and add the EmpRef to the request" in {
        val (application, controller) = runWith(Enrolments(Set(TestEnrolments.payeOrg())))

        running(application) {
          val result = controller.onPageLoadShowingReference()(FakeRequest())

          status(result) mustBe OK
          contentAsString(result) mustBe "123/AB45678"
        }
      }

      "must use the agent reference when the user has both activated enrolments" in {
        val (application, controller) = runWith(Enrolments(Set(TestEnrolments.payeAgent(), TestEnrolments.payeOrg())))

        running(application) {
          val result = controller.onPageLoadShowingReference()(FakeRequest())

          status(result) mustBe OK
          contentAsString(result) mustBe "AB1234"
        }
      }

      "must redirect to the activate enrolment page when the agent enrolment is not activated" in {
        val (application, controller) = runWith(Enrolments(Set(TestEnrolments.payeAgent("NotYetActivated"))))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.ActivateEnrolmentController.onPageLoad().url
        }
      }

      "must redirect to the activate enrolment page when the organisation enrolment is not activated" in {
        val (application, controller) = runWith(Enrolments(Set(TestEnrolments.payeOrg("NotYetActivated"))))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.ActivateEnrolmentController.onPageLoad().url
        }
      }

      "must redirect to the unauthorised page when the user has no PAYE enrolments" in {
        val (application, controller) = runWith(Enrolments(Set.empty))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }

      "must add the credential id to the request" in {
        val (application, controller) = runWith(Enrolments(Set(TestEnrolments.payeAgent())))

        running(application) {
          val result = controller.onPageLoadShowingCredentialId()(FakeRequest())

          status(result) mustBe OK
          contentAsString(result) mustBe TestEnrolments.credentialId
        }
      }

      "must fail with an UnauthorizedException when auth does not return credentials" in {
        val (application, controller) = runWith(Enrolments(Set(TestEnrolments.payeAgent())), credentials = None)

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          result.failed.futureValue mustBe an[UnauthorizedException]
        }
      }

      "must fail with an UnauthorizedException when auth does not return an internal id" in {
        val (application, controller) = runWith(Enrolments(Set(TestEnrolments.payeAgent())), internalId = None)

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          result.failed.futureValue mustBe an[UnauthorizedException]
        }
      }
    }
  }
}

class FakeFailingAuthConnector @Inject()(exceptionToReturn: Throwable) extends AuthConnector {
  val serviceUrl: String = ""

  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
    Future.failed(exceptionToReturn)
}

class FakeIdAndEnrolmentsAuthConnector(
  internalId: Option[String],
  enrolments: Enrolments,
  credentials: Option[Credentials] = Some(TestEnrolments.credentials)
) extends AuthConnector {
  val serviceUrl: String = ""

  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
    Future.successful(new ~(new ~(internalId, credentials), enrolments).asInstanceOf[A])
}
