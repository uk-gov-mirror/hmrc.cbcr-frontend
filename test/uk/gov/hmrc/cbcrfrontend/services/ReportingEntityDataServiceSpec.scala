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

package uk.gov.hmrc.cbcrfrontend.services

import java.time.LocalDate

import org.mockito.ArgumentMatchers.any
import cats.data.NonEmptyList
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.cbcrfrontend.connectors.CBCRBackendConnector
import uk.gov.hmrc.cbcrfrontend.controllers.CSRFTest
import uk.gov.hmrc.cbcrfrontend.model._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.cbcrfrontend.util.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class ReportingEntityDataServiceSpec
    extends UnitSpec with ScalaFutures with GuiceOneAppPerSuite with CSRFTest with MockitoSugar {

  val connector = mock[CBCRBackendConnector]
  val reds = new ReportingEntityDataService(connector)
  val docRefId = DocRefId("GB2016RGXVCBC0000000056CBC40120170311T090000X_7000000002OECD1REP").get
  val crnDocRefId = DocRefId("GB2016RGXVCBC0000000056CBC40120170311T090000A_7000000002OECD1REP").get
  val corrDocRefId = CorrDocRefId(crnDocRefId)
  val cbcid = CBCId.create(1).toOption
  val tin = TIN("90000000001", "")
  val reportingPeriod = "2016-03-31"
  val docRefIdpair = DocRefIdPair(docRefId: DocRefId, Some(corrDocRefId))

  val red = ReportingEntityData(
    NonEmptyList(docRefId, Nil),
    List(docRefId),
    docRefId,
    TIN("90000000001", ""),
    UltimateParentEntity("Foo Corp"),
    CBC701,
    Some(LocalDate.now()),
    None,
    Some("USD"),
    Some(EntityReportingPeriod(LocalDate.parse("2016-01-01"), LocalDate.parse("2016-03-31")))
  )

  val redModel = ReportingEntityDataModel(
    NonEmptyList(docRefId, Nil),
    List(docRefId),
    docRefId,
    TIN("90000000001", ""),
    UltimateParentEntity("Foo Corp"),
    CBC701,
    Some(LocalDate.now()),
    None,
    true,
    Some("USD"),
    None
  )

  DocRefIdPair(docRefId: DocRefId, Some(corrDocRefId))

  val partialRed = PartialReportingEntityData(
    List(docRefIdpair),
    List(docRefIdpair),
    docRefIdpair,
    TIN("90000000001", ""),
    UltimateParentEntity("Foo Corp"),
    CBC701,
    Some(LocalDate.now()),
    None,
    Some("USD"),
    Some(EntityReportingPeriod(LocalDate.parse("2016-01-01"), LocalDate.parse("2016-03-31")))
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "ReportingEntityDataService" should {
    "provide a query service" which {
      "returns a reportingEntityData object the call to the connector returns one" in {
        when(connector.reportingEntityDataQuery(any())(any())) thenReturn Future.successful(
          HttpResponse(Status.OK, Some(Json.toJson(red))))
        val result = Await.result(reds.queryReportingEntityData(docRefId).value, 2.seconds)
        result shouldBe Right(Some(red))
      }
      "return NONE if the connector returns a NotFoundException" in {
        when(connector.reportingEntityDataQuery(any())(any())) thenReturn Future.failed(
          new NotFoundException("Not found"))
        val result = Await.result(reds.queryReportingEntityData(docRefId).value, 2.seconds)
        result shouldBe Right(None)
      }
      "return an error if there is a serialisation error" in {
        when(connector.reportingEntityDataQuery(any())(any())) thenReturn Future.successful(
          HttpResponse(Status.OK, Some(JsString("Not the correct json"))))
        val result = Await.result(reds.queryReportingEntityData(docRefId).value, 2.seconds)
        result.isLeft shouldBe true
      }
      "return an error if anything else goes wrong" in {
        when(connector.reportingEntityDataQuery(any())(any())) thenReturn Future.failed(
          new Exception("The sky is falling"))
        val result = Await.result(reds.queryReportingEntityData(docRefId).value, 2.seconds)
        result.isLeft shouldBe true
      }
    }

    "provide a query service by cbc id and reporting period to prevent multiple file upload for original submission within same reporting period" which {
      "returns a reportingEntityData object the call to the connector returns one" in {
        when(connector.reportingEntityCBCIdAndReportingPeriod(any(), any())(any())) thenReturn Future.successful(
          HttpResponse(Status.OK, Some(Json.toJson(red))))
        val result = Await.result(reds.queryReportingEntityDataByCbcId(cbcid.get, LocalDate.now()).value, 2.seconds)
        result shouldBe Right(Some(red))
      }
      "return NONE if the connector returns a NotFoundException" in {
        when(connector.reportingEntityCBCIdAndReportingPeriod(any(), any())(any())) thenReturn Future.failed(
          new NotFoundException("Not found"))
        val result = Await.result(reds.queryReportingEntityDataByCbcId(cbcid.get, LocalDate.now()).value, 2.seconds)
        result shouldBe Right(None)
      }
      "return an error if there is a serialisation error" in {
        when(connector.reportingEntityCBCIdAndReportingPeriod(any(), any())(any())) thenReturn Future.successful(
          HttpResponse(Status.OK, Some(JsString("Not the correct json"))))
        val result = Await.result(reds.queryReportingEntityDataByCbcId(cbcid.get, LocalDate.now()).value, 2.seconds)
        result.isLeft shouldBe true
      }
      "return an error if anything else goes wrong" in {
        when(connector.reportingEntityCBCIdAndReportingPeriod(any(), any())(any())) thenReturn Future.failed(
          new Exception("The sky is falling"))
        val result = Await.result(reds.queryReportingEntityDataByCbcId(cbcid.get, LocalDate.now()).value, 2.seconds)
        result.isLeft shouldBe true
      }
    }
  }

  "ReportingEntityDataService" should {
    "return ReportingEntityDataModel if it exists in the DB store" in {
      when(connector.reportingEntityDataModelQuery(any())(any())) thenReturn Future.successful(
        HttpResponse(Status.OK, Some(Json.toJson(redModel))))
      val result = Await.result(reds.queryReportingEntityDataModel(docRefId).value, 2.seconds)
      result shouldBe Right(Some(redModel))
    }

    "return an error if there is a serialisation error" in {
      when(connector.reportingEntityDataModelQuery(any())(any())) thenReturn Future.successful(
        HttpResponse(Status.OK, Some(JsString("Not the correct json"))))
      val result = Await.result(reds.queryReportingEntityDataModel(docRefId).value, 2.seconds)
      result.isLeft shouldBe true
    }

    "return NONE if the connector returns a NotFoundException" in {
      when(connector.reportingEntityDataModelQuery(any())(any())) thenReturn Future.failed(
        new NotFoundException("Not found"))
      val result = Await.result(reds.queryReportingEntityDataModel(docRefId).value, 2.seconds)
      result shouldBe Right(None)
    }

    "return an error if anything else goes wrong" in {
      when(connector.reportingEntityDataModelQuery(any())(any())) thenReturn Future.failed(
        new Exception("The sky is falling"))
      val result = Await.result(reds.queryReportingEntityDataModel(docRefId).value, 2.seconds)
      result.isLeft shouldBe true
    }
  }

  "ReportingEntityDataService on a call to queryReportingEntityDataDocRefId" should {
    "return ReportingEntityData if it exists in the DB store" in {
      when(connector.reportingEntityDocRefId(any())(any())) thenReturn Future.successful(
        HttpResponse(Status.OK, Some(Json.toJson(red))))
      val result = Await.result(reds.queryReportingEntityDataDocRefId(docRefId).value, 2.seconds)
      result shouldBe Right(Some(red))
    }

    "return an error if there is a serialisation error while parsing for ReportingEntityData" in {
      when(connector.reportingEntityDocRefId(any())(any())) thenReturn Future.successful(
        HttpResponse(Status.OK, Some(JsString("Not the correct json"))))
      val result = Await.result(reds.queryReportingEntityDataDocRefId(docRefId).value, 2.seconds)
      result.isLeft shouldBe true
    }

    "return NONE if the connector returns a NotFoundException" in {
      when(connector.reportingEntityDocRefId(any())(any())) thenReturn Future.failed(new NotFoundException("Not found"))
      val result = Await.result(reds.queryReportingEntityDataDocRefId(docRefId).value, 2.seconds)
      result shouldBe Right(None)
    }

    "return an error if anything else goes wrong" in {
      when(connector.reportingEntityDocRefId(any())(any())) thenReturn Future.failed(
        new Exception("The sky is falling"))
      val result = Await.result(reds.queryReportingEntityDataDocRefId(docRefId).value, 2.seconds)
      result.isLeft shouldBe true
    }
  }

  "ReportingEntityDataService on a call to queryReportingEntityDataTin" should {
    "return ReportingEntityData if it exists in the DB store" in {
      when(connector.reportingEntityDataQueryTin(any(), any())(any())) thenReturn Future.successful(
        HttpResponse(Status.OK, Some(Json.toJson(red))))
      val result = Await.result(reds.queryReportingEntityDataTin(tin.value, reportingPeriod).value, 2.seconds)
      result shouldBe Right(Some(red))
    }

    "return an error if there is a serialisation error while parsing for ReportingEntityData" in {
      when(connector.reportingEntityDataQueryTin(any(), any())(any())) thenReturn Future.successful(
        HttpResponse(Status.OK, Some(JsString("Not the correct json"))))
      val result = Await.result(reds.queryReportingEntityDataTin(tin.value, reportingPeriod).value, 2.seconds)
      result.isLeft shouldBe true
    }

    "return NONE if the connector returns a NotFoundException" in {
      when(connector.reportingEntityDataQueryTin(any(), any())(any())) thenReturn Future.failed(
        new NotFoundException("Not found"))
      val result = Await.result(reds.queryReportingEntityDataTin(tin.value, reportingPeriod).value, 2.seconds)
      result shouldBe Right(None)
    }

    "return an error if anything else goes wrong" in {
      when(connector.reportingEntityDataQueryTin(any(), any())(any())) thenReturn Future.failed(
        new Exception("The sky is falling"))
      val result = Await.result(reds.queryReportingEntityDataTin(tin.value, reportingPeriod).value, 2.seconds)
      result.isLeft shouldBe true
    }
  }

  "ReportingEntityDataService on a call to updateReportingEntityData" should {
    "update ReportingEntityData if it exists in the DB store" in {
      when(connector.reportingEntityDataUpdate(any())(any())) thenReturn Future.successful(HttpResponse(Status.OK))
      val result = Await.result(reds.updateReportingEntityData(partialRed).value, 2.seconds)
      result shouldBe Right()
    }
    "return an error if anything else goes wrong" in {
      when(connector.reportingEntityDataUpdate(any())(any())) thenReturn Future.failed(
        new Exception("The sky is falling"))
      val result = Await.result(reds.updateReportingEntityData(partialRed).value, 2.seconds)
      result.isLeft shouldBe true
    }
  }

  "ReportingEntityDataService on a call to saveReportingEntityData" should {
    "save ReportingEntityData if it does not exist in the DB store" in {
      when(connector.reportingEntityDataSave(any())(any())) thenReturn Future.successful(HttpResponse(Status.OK))
      val result = Await.result(reds.saveReportingEntityData(red).value, 2.seconds)
      result shouldBe Right()
    }
    "return an error if anything else goes wrong" in {
      when(connector.reportingEntityDataSave(any())(any())) thenReturn Future.failed(
        new Exception("The sky is falling"))
      val result = Await.result(reds.saveReportingEntityData(red).value, 2.seconds)
      result.isLeft shouldBe true
    }
  }

}
