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

import java.time.{LocalDate, LocalDateTime, Year}

import cats.Show
import cats.syntax.show._
import play.api.Logger

import scala.util.control.Exception._
import play.api.libs.json._

/** These models represent the raw data extracted from the XML file*/
sealed trait RawXmlFields extends Product with Serializable

case object RawBody extends RawXmlFields
case class RawAdditionalInfo(docSpec: RawDocSpec, otherInfo: String) extends RawXmlFields
case class RawCbcReports(docSpec: RawDocSpec) extends RawXmlFields
case class RawCurrencyCodes(currCodes: List[String]) extends RawXmlFields
case class RawDocSpec(docType: String, docRefId: String, corrDocRefId: Option[String], corrMessageRefId: Option[String])
    extends RawXmlFields
case class RawCbcVal(cbcVer: String) extends RawXmlFields
case class RawConstEntityName(name: String) extends RawXmlFields
case class RawXmlEncodingVal(xmlEncodingVal: String) extends RawXmlFields
case class RawMessageSpec(
  messageRefID: String,
  receivingCountry: String,
  sendingEntityIn: String,
  timestamp: String,
  reportingPeriod: String,
  messageType: Option[String],
  corrMessageRefId: Option[String])
    extends RawXmlFields
case class RawReportingEntity(
  reportingRole: String,
  docSpec: RawDocSpec,
  tin: String,
  tinIssuedBy: String,
  name: String,
  city: Option[String],
  startDate: String,
  endDate: String)
    extends RawXmlFields
case class RawXMLInfo(
  messageSpec: RawMessageSpec,
  reportingEntity: Option[RawReportingEntity],
  cbcReport: List[RawCbcReports],
  additionalInfo: List[RawAdditionalInfo],
  cbcVal: RawCbcVal,
  xmlEncoding: Option[RawXmlEncodingVal],
  numBodies: Int,
  constEntityNames: List[String],
  currencyCodes: List[RawCurrencyCodes])
    extends RawXmlFields

/** These models represent the type-validated data, derived from the raw data */
class DocRefId private[model] (
  val msgRefID: MessageRefID,
  val tin: String,
  val docTypeIndic: DocTypeIndic,
  val parentGroupElement: ParentGroupElement,
  val uniq: String) {

  override def equals(obj: scala.Any): Boolean = obj match {
    case d: DocRefId => d.show == this.show
    case _           => false
  }

  override def toString: String = this.show

  override def hashCode(): Int = this.show.hashCode()

}
object DocRefId {

  val docRefIdRegex = s"""(${MessageRefID.messageRefIDRegex})_(.{1,30})(OECD[0123])(ENT|REP|ADD)(.{0,41})""".r
  def apply(s: String): Option[DocRefId] = s match {
    case docRefIdRegex(msgRef, _, _, _, _, _, _, tin, docType, pGroup, uniq) =>
      for {
        m <- MessageRefID(msgRef).toOption
        o <- DocTypeIndic.fromString(docType)
        p <- ParentGroupElement.fromString(pGroup)
      } yield new DocRefId(m, tin, o, p, uniq)
    case _ => None
  }

  val newDocRefIdRegex = s"""(${MessageRefID.messageRefIDRegex})_(.{1,30})(OECD[0123])(ENT|REP|ADD)(.{0,25})""".r
  def applyNewDocRefIdRegex(s: String): Option[DocRefId] = s match {
    case newDocRefIdRegex(msgRef, _, _, _, _, _, _, tin, docType, pGroup, uniq) =>
      for {
        m <- MessageRefID(msgRef).toOption
        o <- DocTypeIndic.fromString(docType)
        p <- ParentGroupElement.fromString(pGroup)
      } yield new DocRefId(m, tin, o, p, uniq)
    case _ => None
  }

  implicit val showDocRefId: Show[DocRefId] =
    Show.show[DocRefId](d => s"${d.msgRefID.show}_${d.tin}${d.docTypeIndic}${d.parentGroupElement}${d.uniq}")

  implicit val format = new Format[DocRefId] {

    override def reads(json: JsValue): JsResult[DocRefId] = json match {
      case JsString(s) if apply(s).isDefined =>
        DocRefId(s).fold[JsResult[DocRefId]](JsError(s"Unable to deserialize $s as a DocRefId"))(d => JsSuccess(d))
      case other => JsError(s"Unable to deserialize $other as a DocRefId")
    }

    override def writes(o: DocRefId): JsValue = JsString(o.show)

  }

}

case class CorrDocRefId(cid: DocRefId)
object CorrDocRefId {
  implicit val format = new Format[CorrDocRefId] {
    override def writes(o: CorrDocRefId): JsValue = JsString(o.cid.show)
    override def reads(json: JsValue): JsResult[CorrDocRefId] = DocRefId.format.reads(json).map(CorrDocRefId(_))
  }
}

case class DocSpec(
  docType: DocTypeIndic,
  docRefId: DocRefId,
  corrDocRefId: Option[CorrDocRefId],
  corrMessageRefId: Option[String])
object DocSpec { implicit val format = Json.format[DocSpec] }

case class AdditionalInfo(docSpec: DocSpec, otherInfo: String)
object AdditionalInfo { implicit val format = Json.format[AdditionalInfo] }

case class CbcReports(docSpec: DocSpec)
object CbcReports { implicit val format = Json.format[CbcReports] }

case class MessageSpec(
  messageRefID: MessageRefID,
  receivingCountry: String,
  sendingEntityIn: CBCId,
  timestamp: LocalDateTime,
  reportingPeriod: LocalDate,
  messageType: Option[MessageTypeIndic],
  corrMessageRefId: Option[String])
object MessageSpec {
  implicit val yearFormat = new Format[Year] {
    override def reads(json: JsValue): JsResult[Year] = json match {
      case JsString(year) =>
        (nonFatalCatch either Year.parse(year)).fold(
          _ => JsError(s"Failed to parse $year as a Year"),
          year => JsSuccess(year)
        )
      case other => JsError(s"Failed to parse $other as a Year")
    }
    override def writes(o: Year): JsValue = JsString(o.toString)
  }
  implicit val format = Json.format[MessageSpec]
}

case class EntityReportingPeriod(startDate: LocalDate, endDate: LocalDate)
object EntityReportingPeriod { implicit val format = Json.format[EntityReportingPeriod] }

case class ReportingEntity(
  reportingRole: ReportingRole,
  docSpec: DocSpec,
  tin: TIN,
  name: String,
  city: Option[String],
  entityReportingPeriod: EntityReportingPeriod)

object ReportingEntity { implicit val format = Json.format[ReportingEntity] }

case class CbcOecdInfo(cbcVer: String)
object CbcOecdInfo { implicit val format = Json.format[CbcOecdInfo] }

case class XMLInfo(
  messageSpec: MessageSpec,
  reportingEntity: Option[ReportingEntity],
  cbcReport: List[CbcReports],
  additionalInfo: List[AdditionalInfo],
  creationDate: Option[LocalDate],
  constEntityNames: List[String],
  currencyCodes: List[String])
object XMLInfo { implicit val format = Json.format[XMLInfo] }

case class CompleteXMLInfo(
  messageSpec: MessageSpec,
  reportingEntity: ReportingEntity,
  cbcReport: List[CbcReports],
  additionalInfo: List[AdditionalInfo],
  creationDate: Option[LocalDate],
  constEntityNames: List[String],
  currencyCodes: List[String])
object CompleteXMLInfo {
  def apply(x: XMLInfo, reportingEntity: ReportingEntity): CompleteXMLInfo =
    CompleteXMLInfo(
      x.messageSpec,
      reportingEntity,
      x.cbcReport,
      x.additionalInfo,
      x.creationDate,
      x.constEntityNames,
      x.currencyCodes)
  implicit val format = Json.format[CompleteXMLInfo]
}
