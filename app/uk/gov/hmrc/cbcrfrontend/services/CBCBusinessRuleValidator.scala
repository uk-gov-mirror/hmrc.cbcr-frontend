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

package uk.gov.hmrc.cbcrfrontend.services

import java.time.{LocalDate, LocalDateTime, Year}
import javax.inject.Inject

import cats.Applicative
import cats.data.{EitherT, NonEmptyList, Validated, ValidatedNel}
import cats.instances.all._
import cats.syntax.all._
import uk.gov.hmrc.cbcrfrontend.model._
import uk.gov.hmrc.play.http.HeaderCarrier
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}

class CBCBusinessRuleValidator @Inject() (messageRefService:MessageRefIdService,
                                          docRefIdService: DocRefIdService,
                                          subscriptionDataService: SubscriptionDataService,
                                          reportingEntityDataService: ReportingEntityDataService,
                                          configuration: Configuration,
                                          runMode: RunMode
                                         )(implicit ec:ExecutionContext) {

  val oecd2Or3 = "OECD[23]"
  val oecd0Or2Or3 = "OECD[023]"
  val testData = "OECD1[0123]"

  type FutureValidation[A] = Future[ValidatedNel[BusinessRuleErrors, A]]
  type ValidatedBusinessRuleApplicative[A] = ValidatedNel[BusinessRuleErrors, A]
  implicit val testApp = Applicative[Future] compose Applicative[ValidatedBusinessRuleApplicative]

  def validateBusinessRules(in: RawXMLInfo, fileName: String)(implicit hc: HeaderCarrier): EitherT[Future, NonEmptyList[BusinessRuleErrors], XMLInfo] = {
    EitherT(
      (validateMessageRefIdD(in.messageSpec) |@|
        validateReportingEntity(in.reportingEntity, in) |@|
        in.cbcReport.map(_.docSpec).map(validateDocSpec(_, REP)).sequence[FutureValidation, DocSpec] |@|
        in.additionalInfo.map(_.docSpec).map(validateDocSpec(_, ADD)).sequence[FutureValidation, DocSpec] |@|
        validateSendingEntity(in.messageSpec)).map {
        (messageRefIdVal, reportingEntity, cbcDocSpec, addDocSpec, sendingEntity) =>

          val otherRules = (
            validateTestDataPresent(in).toValidatedNel *>
              validateFileName(in.messageSpec, fileName).toValidatedNel *>
              crossValidateDocRefIds( in.reportingEntity.map(_.docSpec.docRefId), in.cbcReport.map(_.docSpec.docRefId), in.additionalInfo.map(_.docSpec.docRefId) ).toValidatedNel *>
              crossValidateCorrDocRefIds( in.cbcReport.map(_.docSpec), in.reportingEntity.map(_.docSpec), in.additionalInfo.map(_.docSpec) ).toValidatedNel *>
              validateMessageTypeIndic(in).toValidatedNel *>
              in.cbcReport.map(r => validateCorrDocRefIdExists(Some(r.docSpec)).toValidatedNel).sequence[({type λ[α] = ValidatedNel[BusinessRuleErrors,α]})#λ,Unit] *>
              validateCorrDocRefIdExists(in.reportingEntity.map(_.docSpec)).toValidatedNel *>
              validateCorrDocRefIdExists(in.additionalInfo.map(_.docSpec)).toValidatedNel *>
              validateMessageTypeIndicCompatible(in).toValidatedNel *>
              validateCbcOecdVersion(in.cbcVal).toValidatedNel *>
              validateXmlEncodingVal(in.xmlEncoding).toValidatedNel *>
              validateReceivingCountry(in.messageSpec).toValidatedNel |@|
              validateMessageTypeIndic(in).toValidatedNel
        ).tupled


        (otherRules |@| messageRefIdVal |@| reportingEntity |@| cbcDocSpec |@| addDocSpec |@| sendingEntity |@| validateReportingPeriod(in.messageSpec).toValidatedNel).map(
            (values, msgRefId, reportingEntity, cbcDocSpec, addDocSpec, _, reportingPeriod) =>
              XMLInfo(
                MessageSpec(
                  msgRefId, values._1,
                  msgRefId.cBCId,
                  msgRefId.creationTimestamp,
                  reportingPeriod,
                  values._2
                ),
                reportingEntity,
                cbcDocSpec.map(CbcReports(_)),
                addDocSpec.map(AdditionalInfo(_))
              )
          ).toEither
      })
  }

  private def validateReportingEntity(re: Option[RawReportingEntity], in: RawXMLInfo)(implicit hc: HeaderCarrier): Future[ValidatedNel[BusinessRuleErrors, ReportingEntity]] =
    re.map { rre =>
      validateDocSpec(rre.docSpec, ENT).map { vds =>

        val rr_tin = for {
          rr <- validateReportingRole(rre)
          tin <- validateTIN(rre,rr)
        } yield (rr,tin)

        (rr_tin.toValidatedNel |@| vds).map((rr_tin, ds) => ReportingEntity(rr_tin._1, ds, rr_tin._2, rre.name))

      }
    }.getOrElse {
      val id = in.cbcReport.find(_.docSpec.corrDocRefId.isDefined).flatMap(_.docSpec.corrDocRefId).orElse(in.additionalInfo.flatMap(_.docSpec.corrDocRefId)).flatMap(DocRefId(_))
      val rr = in.cbcReport.headOption.map(_.docSpec.docType).orElse(in.additionalInfo.map(_.docSpec.docType)).flatMap(DocTypeIndic.fromString)

      (id |@| rr).map { (drid, dti) =>
        reportingEntityDataService.queryReportingEntityData(drid).leftMap{
          cbcErrors => {
            Logger.error(s"Got error back: $cbcErrors")
            throw new Exception(s"Error communicating with backend: $cbcErrors")
          }
        }.subflatMap{
          case Some(red) => Right(ReportingEntity(red.reportingRole, DocSpec(OECD0, red.reportingEntityDRI, None), TIN(red.utr.value), red.ultimateParentEntity.ultimateParentEntity))
          case None      => Left(OriginalSubmissionNotFound)
        }.toValidatedNel
      }.getOrElse{
        Future.successful(OriginalSubmissionNotFound.invalidNel)
      }
    }

  private def validateXmlEncodingVal(xe: Option[RawXmlEncodingVal]): Validated[BusinessRuleErrors, Unit] = {
    xe.fold[Validated[BusinessRuleErrors, Unit]](().valid)(v =>
      if (!v.xmlEncodingVal.equalsIgnoreCase("UTF-8")) {
        XmlEncodingError.invalid
      } else {
        ().valid
      }
    )
  }




  private def validateCbcOecdVersion(cv:RawCbcVal):Validated[BusinessRuleErrors,Unit] = {
    if(cv.cbcVer != configuration.getString(s"${runMode.env}.oecd-schema-version").getOrElse(throw new Exception(s"Missing configuration key: ${runMode.env}.oecd-schema-version"))){
      CbcOecdVersionError.invalid
    } else {
      ().valid
    }
  }

  private def validateMessageTypeIndicCompatible(r:RawXMLInfo):Validated[BusinessRuleErrors,Unit] = {

    lazy val docTypes = List(
      r.additionalInfo.map(_.docSpec.docType),
      r.reportingEntity.map(_.docSpec.docType)
    ).flatten ++ r.cbcReport.map(_.docSpec.docType)

    if(r.messageSpec.messageType.contains(CBC401.toString) && docTypes.exists(_ != OECD1.toString)){
      MessageTypeIndicDocTypeIncompatible.invalid
    } else {
      ().valid
    }
  }

  private def crossValidateCorrDocRefIds(re:List[RawDocSpec], cb:Option[RawDocSpec], ad:Option[RawDocSpec]) : Validated[BusinessRuleErrors,Unit] = {
    val all = (List(cb.map(_.docType),ad.map(_.docType)).flatten ++ re.map(_.docType)).toSet
    if(all.size > 1 && all.contains(OECD1.toString)){
      IncompatibleOECDTypes.invalid
    } else {
      ().valid
    }
  }

  private def validateCorrDocRefIdExists(ds:Option[RawDocSpec]):Validated[BusinessRuleErrors,Unit] = {
   ds.map { d =>
     if (d.docType.matches(oecd2Or3) && d.corrDocRefId.isEmpty) {
       CorrDocRefIdMissing.invalid
     } else if (d.docType == OECD1.toString && d.corrDocRefId.isDefined) {
       CorrDocRefIdNotNeeded.invalid
     } else {
       ().valid
     }
   }
  }.getOrElse(().valid)

  private def crossValidateDocRefIds(re:Option[String],cb:List[String],ad:Option[String]): Validated[BusinessRuleErrors,Unit] = {
    val ids = List(re,ad).flatten ++ cb
    Either.cond(ids.distinct.size == ids.size,(),DocRefIdDuplicate).toValidated
  }

  private def validateDocSpec(d:RawDocSpec,parentGroupElement: ParentGroupElement)(implicit hc:HeaderCarrier) : Future[ValidatedNel[BusinessRuleErrors,DocSpec]] =
    validateDocRefId(d.docRefId, parentGroupElement).zip(validateCorrDocRefId(d.corrDocRefId, parentGroupElement)).map {
      case (x, c) => (DocTypeIndic.fromString(d.docType).toValidNel(InvalidXMLError("Invalid DocTypeIndic"):BusinessRuleErrors) |@| x.toValidatedNel |@| c.toValidatedNel ).map(DocSpec(_, _, _))
    }

  private def validateCorrDocRefId(corrDocRefIdString:Option[String],parentGroupElement: ParentGroupElement)(implicit hc:HeaderCarrier) : Future[Validated[BusinessRuleErrors,Option[CorrDocRefId]]] = {
    corrDocRefIdString.map(DocRefId(_).fold[Future[Validated[BusinessRuleErrors,Option[CorrDocRefId]]]](
      Future.successful(InvalidCorrDocRefId.invalid))(
      d => if(d.parentGroupElement != parentGroupElement) {
        Future.successful(CorrDocRefIdInvalidParentGroupElement.invalid)
      } else {
        docRefIdService.queryDocRefId(d).map {
          case DocRefIdResponses.Valid => Some(CorrDocRefId(d)).valid
          case DocRefIdResponses.Invalid => CorrDocRefIdInvalidRecord.invalid
          case DocRefIdResponses.DoesNotExist => CorrDocRefIdUnknownRecord.invalid
        }
      })
    ).getOrElse(Future.successful(None.valid))
  }

  private def validateDocRefId(docRefIdString:String, parentGroupElement: ParentGroupElement)(implicit hc:HeaderCarrier) : Future[Validated[BusinessRuleErrors,DocRefId]] = {
    DocRefId(docRefIdString).fold[Future[Validated[BusinessRuleErrors,DocRefId]]](
      Future.successful(InvalidDocRefId.invalid))(
      d => if(d.parentGroupElement != parentGroupElement) {
        Future.successful(DocRefIdInvalidParentGroupElement.invalid)
      } else {
        docRefIdService.queryDocRefId(d).map{
          case DocRefIdResponses.DoesNotExist => d.valid
          case _                              => DocRefIdDuplicate.invalid
        }
      }
    )
  }

  private def validateMessageTypeIndic(r:RawXMLInfo) : Validated[BusinessRuleErrors,Option[MessageTypeIndic]] = {
    r.messageSpec.messageType.flatMap(MessageTypeIndic.parseFrom).map{
      case CBC401                                                                      => Some(CBC401).valid
      case CBC402 if !r.cbcReport.forall(_.docSpec.docType.matches(oecd2Or3))
                  || !r.additionalInfo.forall(_.docSpec.docType.matches(oecd2Or3))
                  || !r.reportingEntity.forall(_.docSpec.docType.matches(oecd0Or2Or3)) => MessageTypeIndicError.invalid
      case CBC402                                                                      => Some(CBC402).valid
    }.getOrElse((None:Option[MessageTypeIndic]).valid)

  }

  private def validateTIN(in:RawReportingEntity, rr:ReportingRole) : Either[BusinessRuleErrors, TIN] = {
    rr match {
      case CBC702                                                    => Right(TIN(in.tin))
      case CBC701 | CBC703 if !in.tinIssuedBy.equalsIgnoreCase("gb") => Left(InvalidXMLError("ReportingEntity.Entity.TIN@issuedBy must be 'GB' for voluntary or primary filings"))
      case CBC701 | CBC703 if !Utr(in.tin).isValid                   => Left(InvalidXMLError("ReportingEntity.Entity.TIN must be a valid UTR for filings issued in 'GB'"))
      case _                                                         => Right(TIN(in.tin))
    }
  }

  private def validateReportingRole(in:RawReportingEntity): Either[BusinessRuleErrors, ReportingRole] =
    ReportingRole.parseFromString(in.reportingRole).toRight(InvalidXMLError("ReportingEntity.ReportingRole not found or invalid"))

  private def validateFileName(in:RawMessageSpec,fileName:String) : Validated[BusinessRuleErrors,Unit] =
    if(fileName.split("""\.""").headOption.contains(in.messageRefID)){ ().valid }
    else { FileNameError.invalid }

  private def validateSendingEntity(in:RawMessageSpec)(implicit hc:HeaderCarrier) : Future[ValidatedNel[BusinessRuleErrors,CBCId]] =
    CBCId(in.sendingEntityIn).fold[Future[ValidatedNel[BusinessRuleErrors,CBCId]]](
      Future.successful(SendingEntityError.invalidNel[CBCId]))(
      cbcId => {
        if (CBCId.isPrivateBetaCBCId(cbcId)) {
          Future.successful(PrivateBetaCBCIdError.invalidNel[CBCId])
        } else {
          subscriptionDataService.retrieveSubscriptionData(Right(cbcId)).fold[ValidatedNel[BusinessRuleErrors, CBCId]](
            (_: CBCErrors) => SendingEntityError.invalidNel,
            (maybeDetails: Option[SubscriptionDetails]) => maybeDetails match {
              case None => SendingEntityError.invalidNel
              case Some(_) => cbcId.validNel
            }
          )
        }
      }
    )

  private def validateReceivingCountry(in:RawMessageSpec) : Validated[BusinessRuleErrors,String] =
    if(in.receivingCountry equalsIgnoreCase "GB"){ in.receivingCountry.valid }
    else { ReceivingCountryError.invalid }

  private def validateTestDataPresent(in:RawXMLInfo) : Validated[BusinessRuleErrors,Unit] = {
    val docTypes = List(
      in.additionalInfo.map(_.docSpec.docType),
      in.reportingEntity.map(_.docSpec.docType)
    ).flatten ++ in.cbcReport.map(_.docSpec.docType)

    if(docTypes.exists(_.matches(testData))) {
      TestDataError.invalid
    } else {
      ().valid
    }
  }

  private def validateCBCId(in:RawMessageSpec, messageRefID: MessageRefID) : Validated[MessageRefIDError,Unit] =
    if(in.sendingEntityIn.equalsIgnoreCase(messageRefID.cBCId.value)) { ().valid}
    else MessageRefIDCBCIdMismatch.invalid

  private def validateReportingPeriod(in:RawMessageSpec) : Validated[BusinessRuleErrors,LocalDate] =
    Validated.catchNonFatal(LocalDate.parse(in.reportingPeriod))
      .leftMap(_ => InvalidXMLError("Invalid Date for reporting period"))

  private def validateReportingPeriodMatches(in:RawMessageSpec, year:Year) : Validated[MessageRefIDError,Year] =
    if(in.reportingPeriod.startsWith(year.toString)) { year.valid }
    else { MessageRefIDReportingPeriodMismatch.invalid }

  private def validateDateStamp(in:RawMessageSpec) : Validated[MessageRefIDError,LocalDateTime] =
    Validated.catchNonFatal(LocalDateTime.parse(in.timestamp,MessageRefID.dateFmt)).leftMap(
      _   => MessageRefIDTimestampError
    )

  private def isADuplicate(msgRefId:MessageRefID)(implicit hc:HeaderCarrier) : Future[Validated[MessageRefIDError,Unit]] =
    messageRefService.messageRefIdExists(msgRefId).map(result =>
      if(result) MessageRefIDDuplicate.invalid else ().valid
    )

  private def validateMessageRefIdD(in:RawMessageSpec)(implicit hc:HeaderCarrier) : Future[ValidatedNel[MessageRefIDError, MessageRefID]] = {
    MessageRefID(in.messageRefID).toEither.fold(
      errors   => Future.successful(errors.invalid[MessageRefID]),
      msgRefId => isADuplicate(msgRefId).map(dup =>
        (dup.toValidatedNel |@| validateCBCId(in, msgRefId).toValidatedNel |@| validateReportingPeriodMatches(in, msgRefId.reportingPeriod).toValidatedNel).map(
          (_, _, _) => msgRefId
        )
      )
    )
  }

}
