/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.cbcrfrontend

import javax.inject.Inject

import play.api.{Configuration, Environment}
import play.api.i18n.MessagesApi
import play.api.mvc.{Request, RequestHeader}
import uk.gov.hmrc.auth.core.NoActiveSession
import uk.gov.hmrc.cbcrfrontend.config.FrontendAppConfig
import uk.gov.hmrc.play.bootstrap.config.AuthRedirects
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler

class CBCRErrorHandler @Inject()(val messagesApi: MessagesApi, val env:Environment, val config:Configuration)(implicit val feConfig:FrontendAppConfig) extends FrontendErrorHandler with AuthRedirects{

  override def resolveError(rh: RequestHeader, ex: Throwable) = ex match {
    case _:NoActiveSession => toGGLogin(rh.uri)
  }

  override def standardErrorTemplate (pageTitle: String, heading: String, message: String) (implicit request: Request[_] ) =
  uk.gov.hmrc.cbcrfrontend.views.html.error_template (pageTitle, heading, message)
}