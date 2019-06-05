package com.odenzo.ripple.fixturesgen

import io.circe.syntax._
import java.nio.file.Path
import scala.concurrent.ExecutionContextExecutor

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Json, JsonObject}
import org.scalatest.concurrent.PatienceConfiguration

import com.odenzo.ripple.integration_testkit.{JsonReqRes, RequestResponse, WebSocketJsonConnection, WebSocketJsonQueueFactory}
import com.odenzo.ripple.integration_tests.integration_testkit.IntegrationTestFixture
import com.odenzo.ripple.localops.utils.CirceUtils
import com.odenzo.ripple.localops.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.localops.utils.caterrors.{AppError, OError}
import com.odenzo.ripple.models.atoms.TxBlob
import com.odenzo.ripple.models.support.{RippleEngineResult, RippleGenericError, RippleGenericResponse, RippleGenericSuccess}
import com.odenzo.ripple.models.wireprotocol.transactions.{SubmitRq, SubmitRs}

/**
  * Helpers to run tests and save results to a JSON file for regression testing later.
  */
trait FixtureGeneratorUtils extends IntegrationTestFixture with StrictLogging with PatienceConfiguration {

  private val testNetConnAttempt: ErrorOr[WebSocketJsonConnection] = new WebSocketJsonQueueFactory(testNode).connect()
  val testServerConn: WebSocketJsonConnection                      = getOrLog(testNetConnAttempt)
  implicit val ec: ExecutionContextExecutor                        = ecGlobal

  /** This will strip "field"=null */
  def reqres2string(rr: JsonReqRes): String = {
    val rq = CirceUtils.print(rr.rq)
    val rs = CirceUtils.print(rr.rs)
    s"""\n\n{\n\t "Request":$rq, \n\t "Response":$rs\n } """
  }

  /**  Quick hack to log in a way we can paste into regression testing files */
  def logAnswer(ans: ErrorOr[JsonReqRes]): Unit = {
    ans.foreach(rr ⇒ logger.info(reqres2string(rr)))

  }

  /** Quick hack to log in a way we can paste into regression testing files */
  def logAnswer(ans: List[JsonReqRes]): Unit = {
    val arr = ans.map(reqres2string).mkString("[\n", ",\n", "\n]")
    logger.info(arr)

  }

  def logAnswerToFile(file: String, ans: List[JsonReqRes]): Path = {
    val arr: String = ans.map(reqres2string).mkString("[\n", ",\n", "\n]")
    overwriteFileWith(file, arr)

  }

  import cats.implicits._

  def doFixture(requests: List[JsonObject]): Either[AppError, List[String]] = {
    requests.traverse { rq ⇒
      doCall(rq).map(reqres2string)
    }
  }

  def doDecodingCall[T](rq: JsonObject, decoder: Decoder[T]): Either[AppError, T] = {
    doCall(rq).flatMap(v ⇒ CirceUtils.decode(v.rs, decoder))
  }

  def doCall(rq: JsonObject): ErrorOr[JsonReqRes] = {
    // Serializer does this too, but needed for clean fixture generation
    val cleanRq = CirceUtils.pruneNullFields(rq)
    testServerConn.ask(cleanRq)
  }

  /** Shifts generic and engine failures to left */
  def decodeTxnCall[T](rr: JsonReqRes, decoder: Decoder[T]): Either[AppError, T] = {
    val grs: RippleGenericResponse = getOrLog(CirceUtils.decode(rr.rs, Decoder[RippleGenericResponse]))
    grs match {
      case ok: RippleGenericSuccess ⇒
        val res =  shiftEngineErrorToLeft(ok).flatMap(v⇒ CirceUtils.decode(v.result, decoder) )
        res.left.foreach { err: AppError ⇒
             logger.error(s"Txn Error: ${err.show}  \n Conversation: ${reqres2string(rr)}")
        }
          res

      case bad: RippleGenericError  ⇒
        logger.debug(s"Response: ${rr.rs.spaces4}")
        logger.error(s"Bad Result $bad \nFrom\n ${rr.rq.spaces4}")
        logAnswer(rr.asRight)
        AppError("Failed Transaction").asLeft
    }
  }

  def doCmdCall[T](rq: JsonObject, resultDecoder: Decoder[T]): Either[AppError, T] = {
    doCmdCallKeepJson(rq, resultDecoder).map(_._1)
  }

  /** Sends the rqJson object to rippled server, stripping x=null fields.
  *
    * @param rq
    * @param decoder Decoder to apply to the result field of the response json.
    * @tparam T
    * @return JSON Request and Response, w/ Request stripped of null fields.
    */
  def doCmdCallKeepJson[T](rq: JsonObject, decoder: Decoder[T]): Either[AppError, (T, JsonReqRes)] = {
    val jsons: ErrorOr[JsonReqRes] = doCall(rq)
    val res: Either[AppError, T]   = jsons.flatMap(v => parseReqRes(v, decoder))
    (res, jsons).mapN((_, _))
  }

  /** Do the call shifting generic and txn engine errors to Left */
  def doTxnCall[T](rq: JsonObject, decoder: Decoder[T]): Either[AppError, T] = {
    doCall(rq).flatMap(parseTxnRqRs(_, decoder))
  }

  /** Do the call shifting generic and txn engine errors to Left */
  def doTxnCallKeepJson[T](rq: JsonObject, decoder: Decoder[T]): Either[AppError, (T, JsonReqRes)] = {
    for {
     rr ← doCall(rq)
     obj ← parseTxnRqRs(rr,decoder)
    } yield (obj,rr)
  }


  /** Filters out success = false generic errors */
  def parseReqRes[T](rr: JsonReqRes, decoder: Decoder[T]): Either[AppError, T] = {
    val grs = CirceUtils.decode(rr.rs, Decoder[RippleGenericResponse])
    grs.flatMap {
      case ok: RippleGenericSuccess ⇒ CirceUtils.decode(ok.result, decoder)
      case bad: RippleGenericError ⇒
        logger.debug(s"Response: ${rr.rs.spaces4}")
        logger.error(s"Bad Result $bad \nFrom\n ${rr.rq.spaces4}")
        logAnswer(rr.asRight)
        AppError("Failed ").asLeft
    }
  }

  def shiftGenericErrorsToLeft(res: RippleGenericResponse): Either[OError, RippleGenericSuccess] = {
    res match {
      case ok: RippleGenericSuccess ⇒ ok.asRight
      case bad: RippleGenericError  ⇒ AppError("GenericResponseError ").asLeft
    }
  }

  def shiftEngineErrorToLeft(success: RippleGenericSuccess): Either[AppError, RippleGenericSuccess] = {
    val engine = CirceUtils.decode(success.result, RippleEngineResult.decoder)
    engine.flatMap { er =>
      if (er.isSuccess) success.asRight
      else new OError(s"Engine Failure ${er.engine_result_message} " + er).asLeft
    }
  }
  def parseTxnRqRs[T](rr: JsonReqRes, decoder: Decoder[T]): Either[AppError, T] = {

    CirceUtils
      .decode(rr.rs, Decoder[RippleGenericResponse])
      .flatMap(shiftGenericErrorsToLeft)
      .flatMap((v: RippleGenericSuccess) => shiftEngineErrorToLeft(v))
      .flatMap(success => CirceUtils.decode(success.result, decoder))

  }

  /** Worth a expirement with Cats Resource handling! */
  def overwriteFileWith(filePath: String, data: String): Path = {
    import java.nio.charset.StandardCharsets
    import java.nio.file.{Files, Paths}
    Files.write(Paths.get(filePath), data.getBytes(StandardCharsets.UTF_8))
  }
}
