package com.odenzo.ripple.fixturesgen

import java.nio.file.Path

import scala.concurrent.ExecutionContextExecutor
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Json}
import org.scalatest.concurrent.PatienceConfiguration
import com.odenzo.ripple.fixturesgen.ScenerioBuilder.{getOrLog, logger}
import com.odenzo.ripple.integration_testkit.{RequestResponse, WebSocketJsonConnection, WebSocketJsonQueueFactory}
import com.odenzo.ripple.integration_tests.integration_testkit.IntegrationTestFixture
import com.odenzo.ripple.models.support.{RippleEngineResult, RippleGenericError, RippleGenericResponse, RippleGenericSuccess}
import com.odenzo.ripple.models.wireprotocol.accountinfo.WalletProposeRs
import com.odenzo.ripple.localops.utils.CirceUtils
import com.odenzo.ripple.localops.utils.caterrors.{AppError, AppJsonDecodingError, AppRippleError, OError}
import com.odenzo.ripple.localops.utils.caterrors.CatsTransformers.ErrorOr
import io.circe.Decoder.Result

/**
  * Helpers to run tests and save results to a JSON file for regression testing later.
  */
trait FixtureGeneratorUtils extends IntegrationTestFixture with StrictLogging with PatienceConfiguration {

  private val testNetConnAttempt: ErrorOr[WebSocketJsonConnection] = new WebSocketJsonQueueFactory(testNode).connect()
  val testServerConn: WebSocketJsonConnection  = getOrLog(testNetConnAttempt)
  implicit val ec: ExecutionContextExecutor = ecGlobal

  /** This will strip "field"=null */
  def reqres2string(rr: RequestResponse[Json, Json]): String = {
    val rq = CirceUtils.print(rr.rq)
    val rs = CirceUtils.print(rr.rs)
    s"""\n\n{\n\t "Request":$rq, \n\t "Response":$rs\n } """
  }

  /**  Quick hack to log in a way we can paste into regression testing files */
  def logAnswer(ans: ErrorOr[RequestResponse[Json, Json]]) = {
    ans.foreach(rr ⇒ logger.info(reqres2string(rr)))

  }

  /** Quick hack to log in a way we can paste into regression testing files */
  def logAnswer(ans: List[RequestResponse[Json, Json]]): Unit = {
    val arr = ans.map(reqres2string).mkString("[\n", ",\n", "\n]")
    logger.info(arr)

  }

  def logAnswerToFile(file: String, ans: List[RequestResponse[Json, Json]]): Path = {
    val arr: String = ans.map(reqres2string).mkString("[\n", ",\n", "\n]")
    overwriteFileWith(file, arr)

  }

  import cats.implicits._

  def doFixture(requests: List[Json]): Either[AppError, List[String]] = {
    requests.traverse { rq ⇒
      doCall(rq).map(reqres2string)
    }
  }

  def doCmdCall[T](rq:Json, resultDecoder:Decoder[T]): Either[AppError, T] = {
    doCmdCallKeepJson(rq,resultDecoder).map(_._1)
  }

  def doCmdCallKeepJson[T](rq:Json, decoder:Decoder[T]): Either[AppError, (T, RequestResponse[Json, Json])] = {
    val jsons: ErrorOr[RequestResponse[Json, Json]] = doCall(rq)
    val res: Either[AppError, T] = jsons.flatMap(v=> parseReqRes(v,decoder))
    (res,jsons).mapN( (_,_))
  }

  /** Do the call shifting generic and txn engine errors to Left */
  def doTxnCall[T](rq: Json, decoder: Decoder[T]): Either[AppError, T] = {
     doCall(rq).flatMap(parseTxnRqRs(_,decoder))
  }

  /** Filters out success = false generic errors */
  def parseReqRes[T](rr:RequestResponse[Json,Json], decoder:Decoder[T]): Either[AppError, T] = {
    val grs: Either[AppJsonDecodingError, RippleGenericResponse] = CirceUtils.decode(rr.rs,Decoder[RippleGenericResponse])
    grs.flatMap {
      case ok: RippleGenericSuccess ⇒ CirceUtils.decode(ok.result, decoder)
      case bad: RippleGenericError ⇒
        logger.debug(s"Response: ${rr.rs.spaces4}")
        logger.error(s"Bad Result $bad \nFrom\n ${rr.rq.spaces4}")
        logAnswer(rr.asRight)
        AppError("Failed ").asLeft
    }
  }

  def shiftGenericErrorsToLeft(res:RippleGenericResponse): Either[OError, RippleGenericSuccess] = {
     res match  {
      case ok: RippleGenericSuccess ⇒  ok.asRight
      case bad: RippleGenericError ⇒   AppError("GenericResponseError ").asLeft
    }
  }

  def shiftEngineErrorToLeft(success:RippleGenericSuccess): Either[AppError, RippleGenericSuccess] = {
    val engine = CirceUtils.decode(success.result, RippleEngineResult.decoder)
    engine.flatMap { er =>
      if (er.isSuccess) success.asRight
      else new AppRippleError(s"Engine Failure ${er.engine_result_message}", er).asLeft
    }
  }
  def parseTxnRqRs[T](rr:RequestResponse[Json,Json], decoder:Decoder[T]): Either[AppError, T] = {

     CirceUtils.decode(rr.rs,Decoder[RippleGenericResponse])
       .flatMap(shiftGenericErrorsToLeft)
         .flatMap((v: RippleGenericSuccess) => shiftEngineErrorToLeft(v))
      .flatMap(success => CirceUtils.decode(success.result,decoder))


  }

  def doDecodingCall[T](rq: Json, decoder: Decoder[T]): Either[AppError, T] = {
    doCall(rq).flatMap(v ⇒ CirceUtils.decode(v.rs, decoder))
  }

  def doCall(rq: Json): ErrorOr[RequestResponse[Json, Json]] = {
    val answer: ErrorOr[RequestResponse[Json, Json]] = testServerConn.ask(rq)
    answer.foreach(rr ⇒ logger.debug("Response: " + rr.rs.spaces4))
    answer
  }

  def decodeTxnCall[T](rr: RequestResponse[Json, Json], decoder: Decoder[T]): Either[AppError, T] = {
    val grs: RippleGenericResponse = getOrLog(CirceUtils.decode(rr.rs, Decoder[RippleGenericResponse]))
    grs match {
      case ok: RippleGenericSuccess ⇒ CirceUtils.decode(ok.result, decoder)
      case bad: RippleGenericError ⇒
        logger.debug(s"Response: ${rr.rs.spaces4}")
        logger.error(s"Bad Result $bad \nFrom\n ${rr.rq.spaces4}")
        logAnswer(rr.asRight)
        AppError("Failed Transaction").asLeft
    }
  }

  /** Worth a expirement with Cats Resource handling! */
  def overwriteFileWith(filePath: String, data: String) = {
    import java.nio.file.{Paths, Files}
    import java.nio.charset.StandardCharsets
    Files.write(Paths.get(filePath), data.getBytes(StandardCharsets.UTF_8))
  }
}
