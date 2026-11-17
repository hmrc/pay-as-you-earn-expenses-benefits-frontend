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
import config.FrontendAppConfig
import controllers.actions.TestEnrolments.*
import controllers.routes
import play.api.mvc.{Action, AnyContent, BodyParsers, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.http.UnauthorizedException

import scala.concurrent.ExecutionContext.Implicits.global

class OrgIdentifierActionSpec extends SpecBase {

  class Harness(authAction: OrgIdentifierAction) {
    def onPageLoad(): Action[AnyContent] = authAction { request => Results.Ok(request.enrolmentReference.value) }
  }

  private def harness(connector: AuthConnector): (Harness, FrontendAppConfig, play.api.Application) = {
    val application = applicationBuilder(userAnswers = None).build()
    val bodyParsers = application.injector.instanceOf[BodyParsers.Default]
    val appConfig   = application.injector.instanceOf[FrontendAppConfig]
    (new Harness(new AuthenticatedOrgIdentifierAction(connector, appConfig, bodyParsers)), appConfig, application)
  }

  "Org Identifier Action" - {

    "when the user is an Organisation with an activated IR-PAYE enrolment" - {

      "must allow the request and add the EmpRef to the request" in {
        val (controller, _, application) =
          harness(new FakeSuccessfulAuthConnector(Some("id"), Some(AffinityGroup.Organisation), Enrolments(Set(payeOrg()))))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe OK
          contentAsString(result) mustBe "123/AB45678"
        }
      }
    }

    "when the user is an Organisation with an IR-PAYE enrolment that is not activated" - {

      "must redirect to the activate enrolment page" in {
        val (controller, _, application) =
          harness(new FakeSuccessfulAuthConnector(Some("id"), Some(AffinityGroup.Organisation), Enrolments(Set(payeOrg("NotYetActivated")))))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.ActivateEnrolmentController.onPageLoad().url
        }
      }
    }

    "when the user is an Organisation without an IR-PAYE enrolment" - {

      "must redirect to the unauthorised page" in {
        val (controller, _, application) =
          harness(new FakeSuccessfulAuthConnector(Some("id"), Some(AffinityGroup.Organisation), Enrolments(Set.empty)))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }
    }

    "when the user is an Agent with an activated IR-PAYE-AGENT enrolment" - {

      "must redirect to Ag1" in {
        val (controller, _, application) =
          harness(new FakeSuccessfulAuthConnector(Some("id"), Some(AffinityGroup.Agent), Enrolments(Set(payeAgent()))))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.Ag1Controller.onPageLoad().url
        }
      }
    }

    "when the user is an Agent that somehow holds an IR-PAYE enrolment" - {

      "must redirect to the unauthorised page" in {
        val (controller, _, application) =
          harness(new FakeSuccessfulAuthConnector(Some("id"), Some(AffinityGroup.Agent), Enrolments(Set(payeOrg()))))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }
    }

    "when the user is an Individual with an IR-PAYE enrolment" - {

      "must redirect to the unauthorised page" in {
        val (controller, _, application) =
          harness(new FakeSuccessfulAuthConnector(Some("id"), Some(AffinityGroup.Individual), Enrolments(Set(payeOrg()))))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }
    }

    "when the user is an Individual holding both activated enrolments" - {

      "must redirect to the unauthorised page rather than bouncing between AG1 and ORG1" in {
        val (controller, _, application) =
          harness(new FakeSuccessfulAuthConnector(Some("id"), Some(AffinityGroup.Individual), Enrolments(Set(payeAgent(), payeOrg()))))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }
    }

    "when auth does not return an internal id" - {

      "must fail with an UnauthorizedException" in {
        val (controller, _, application) =
          harness(new FakeSuccessfulAuthConnector(None, Some(AffinityGroup.Organisation), Enrolments(Set(payeOrg()))))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          result.failed.futureValue mustBe an[UnauthorizedException]
        }
      }
    }

    "when the user hasn't logged in" - {

      "must redirect the user to log in" in {
        val (controller, appConfig, application) = harness(new FakeFailingAuthConnector(new MissingBearerToken))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value must startWith(appConfig.loginUrl)
        }
      }
    }

    "when the user's session has expired" - {

      "must redirect the user to log in" in {
        val (controller, appConfig, application) = harness(new FakeFailingAuthConnector(new BearerTokenExpired))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value must startWith(appConfig.loginUrl)
        }
      }
    }

    "when the user used an unaccepted auth provider" - {

      "must redirect the user to the unauthorised page" in {
        val (controller, _, application) = harness(new FakeFailingAuthConnector(new UnsupportedAuthProvider))

        running(application) {
          val result = controller.onPageLoad()(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result).value mustBe routes.UnauthorisedController.onPageLoad().url
        }
      }
    }
  }
}
