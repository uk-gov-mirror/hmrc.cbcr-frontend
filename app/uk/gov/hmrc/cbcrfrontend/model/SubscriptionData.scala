/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json._
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.emailaddress.PlayJsonFormats._

case class CbcId(id:String)
object CbcId{
  implicit val cbcIdFormat:Format[CbcId] = new Format[CbcId] {
    override def writes(o: CbcId): JsValue = JsString(o.id)

    override def reads(json: JsValue): JsResult[CbcId] = json match {
      case JsString(s) => JsSuccess(CbcId(s))
      case other => JsError(s"Unable to read cbcid: $other")
    }
  }
}

case class SubscriptionData(name:String, role:String, phoneNumber:String, email:EmailAddress, cbcId:CbcId)
object SubscriptionData {
  implicit val subscriptionFormat :Format[SubscriptionData] = Json.format[SubscriptionData]
}
