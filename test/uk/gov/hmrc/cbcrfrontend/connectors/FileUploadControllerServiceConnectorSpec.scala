/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cbcrfrontend.connectors

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.scalatest.{EitherValues, FlatSpec, Matchers}
import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec

import scala.io.Source


class FileUploadControllerServiceConnectorSpec extends UnitSpec with Matchers with EitherValues {



  "A FileUploadControllerServiceConnectorSpec " should {
    "create the expected Json Object when the expiry Date is specified" in {
      val formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd'T'HH:mm:ss'Z'")
      val envelopeExpiryDate = LocalDateTime.now.plusDays(7).format(formatter)

      val expectedEnvelopeRequest = Json.obj(
        "callbackUrl" -> "http://localhost:9797/cbcr/file-upload-response",
        "expiryDate" -> s"$envelopeExpiryDate",
        "metadata" -> Json.obj(
          "application" -> "Country By Country Reporting Service"
        ),
        "constraints" -> Json.obj(
          "maxSize" -> "50MB",
          "maxSizePerItem" -> "50MB",
          "contentTypes" -> List("application/xml","text/xml")
        )
      )

      val actualEnvelopeRequest = new FileUploadServiceConnector().envelopeRequest("http://localhost:9797", Some(envelopeExpiryDate))

      actualEnvelopeRequest should be(expectedEnvelopeRequest)
    }

    "return the expected Json Object when the expiry Date is Not specified" in {
      val formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd'T'HH:mm:ss'Z'")
      val envelopeExpiryDate = LocalDateTime.now.plusDays(7).format(formatter)

      val expectedEnvelopeRequest = Json.obj(
        "callbackUrl" -> "http://localhost:9797/cbcr/file-upload-response",
        "metadata" -> Json.obj(
          "application" -> "Country By Country Reporting Service"
        ),
        "constraints" -> Json.obj(
          "maxSize" -> "50MB",
          "maxSizePerItem" -> "50MB",
          "contentTypes" -> List("application/xml","text/xml")
        )
      )

      val actualEnvelopeRequest = new FileUploadServiceConnector().envelopeRequest("http://localhost:9797", None)

      actualEnvelopeRequest should be(expectedEnvelopeRequest)
    }
  }
}
