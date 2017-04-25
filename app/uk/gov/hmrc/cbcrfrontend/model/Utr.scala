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

import play.api.libs.json.{Reads, Writes}
import uk.gov.hmrc.domain.{Modulus11Check, SimpleObjectReads, SimpleObjectWrites, TaxIdentifier}

/**
  * Created by max on 10/04/17.
  */
case class Utr(utr: String) extends TaxIdentifier {
  override def value: String = utr

  def isValid: Boolean = CheckUTR.isValid(utr)

  object CheckUTR extends Modulus11Check {
    def isValid(utr: String): Boolean = utr match {
      case Utr.utrPattern(_*) =>
        val suffix: String = utr.substring(1)
        val checkCharacter: Char = calculateCheckCharacter(suffix)
        checkCharacter == utr.charAt(0)
      case _ => false
    }
  }
}

object Utr {
  private val utrPattern = "^[0-9]{10}$".r

  implicit val utrFormat: Writes[Utr] = new SimpleObjectWrites[Utr](_.value)
  implicit val utrRead: Reads[Utr] = new SimpleObjectReads[Utr]("utr", Utr.apply)
}