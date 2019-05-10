package com.odenzo.ripple.fixturesgen

import scala.concurrent.ExecutionContextExecutor

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Json}
import org.scalatest.concurrent.PatienceConfiguration

import com.odenzo.ripple.fixturesgen.ScenerioBuilder.{getOrLog, logger}
import com.odenzo.ripple.integration_testkit.{RequestResponse, WebSocketJsonConnection, WebSocketJsonQueueFactory}
import com.odenzo.ripple.integration_tests.integration_testkit.IntegrationTestFixture
import com.odenzo.ripple.models.support.{RippleGenericError, RippleGenericResponse, RippleGenericSuccess}
import com.odenzo.ripple.models.wireprotocol.accountinfo.WalletProposeRs
import com.odenzo.ripple.utils.CirceUtils
import com.odenzo.ripple.utils.caterrors.AppError
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr

/**
  * Helpers to run tests and save results to a JSON file for regression testing later.
  */
trait FixtureGeneratorUtils extends IntegrationTestFixture with StrictLogging with PatienceConfiguration {

  val testNetConnAttempt: ErrorOr[WebSocketJsonConnection] = new WebSocketJsonQueueFactory(testNode).connect()

  testNetConnAttempt.left.foreach(e ⇒ logger.info("Connecting:" + AppError.summary(e)))

  val testNetConn: WebSocketJsonConnection  = testNetConnAttempt.right.value
  implicit val ec: ExecutionContextExecutor = ecGlobal

  def reqres2string(rr: RequestResponse[Json, Json]): String = {
    s"""\n\n{\n\t "Request":${rr.rq.spaces4}, \n\t "Response":${rr.rs.spaces4}\n } """
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

  def logAnswerToFile(file: String, ans: List[RequestResponse[Json, Json]]): Unit = {
    val arr: String = ans.map(reqres2string).mkString("[\n", ",\n", "\n]")
    overwriteFileWith(file, arr)

  }

  import cats.implicits._

  def doFixture(requests: List[Json]): Either[AppError, List[String]] = {
    requests.traverse { rq ⇒
      doCall(rq).map(reqres2string)
    }
  }

  def doTxnCall[T](rq: Json, decoder: Decoder[T]): Either[AppError, T] = {
    // logger.debug(s"Doing Call ${rq.spaces4}")
    val rr: RequestResponse[Json, Json] = getOrLog(doCall(rq), "Doing Basic Call")
    parseTxnReqRes(rr,decoder)
  }

  def parseTxnReqRes[T]( rr:RequestResponse[Json,Json], decoder:Decoder[T]): Either[AppError, T] = {
    val grs: RippleGenericResponse      = getOrLog(CirceUtils.decode(rr.rs,Decoder[RippleGenericResponse]))
    grs match {
      case ok: RippleGenericSuccess ⇒ CirceUtils.decode(ok.result, decoder)
      case bad: RippleGenericError ⇒
        logger.debug(s"Response: ${rr.rs.spaces4}")
        logger.error(s"Bad Result $bad \nFrom\n ${rr.rq.spaces4}")
        logAnswer(rr.asRight)
        AppError("Failed Transaction").asLeft
    }
  }

  def doDecodingCall[T](rq: Json, decoder: Decoder[T]): Either[AppError, T] = {
    doCall(rq).flatMap(v ⇒ CirceUtils.decode(v.rs, decoder))
  }

  def doCall(rq: Json): ErrorOr[RequestResponse[Json, Json]] = {
    val answer: ErrorOr[RequestResponse[Json, Json]] = testNetConn.ask(rq)
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
