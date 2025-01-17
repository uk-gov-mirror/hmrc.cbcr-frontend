/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cbcrfrontend.model

import play.api.libs.json.Json

case class MetaData(application: String = "Country By Country Reporting Service")
object MetaData {
  implicit val metaDataFormat = Json.format[MetaData]
}

case class Constraints(
  maxSize: String = "50MB",
  maxSizePerItem: String = "50MB",
  contentTypes: List[String] = List("application/xml", "text/xml"))
object Constraints {
  implicit val constraintFormat = Json.format[Constraints]
}

case class EnvelopeRequest(
  callbackUrl: String,
  expiryDate: Option[String],
  metadata: MetaData,
  constraints: Constraints)
object EnvelopeRequest {
  import MetaData._
  import Constraints._

  implicit val envelopeREquestFormat = Json.format[EnvelopeRequest]
}
