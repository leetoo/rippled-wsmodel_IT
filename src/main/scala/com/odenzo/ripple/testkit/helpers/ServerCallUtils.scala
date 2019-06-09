package com.odenzo.ripple.testkit.helpers

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, JsonObject}

import com.odenzo.ripple.models.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.models.utils.caterrors.{AppError, AppJsonDecodingError, OError}
import com.odenzo.ripple.models.support.{RippleEngineResult, RippleGenericError, RippleGenericResponse, RippleGenericSuccess}
import com.odenzo.ripple.models.utils.CirceUtils

/**
  * *LOW LEVEL* Helper utilities to send commands and transaction to Riplpe Server and parse results
  *  This demands a serverConnection:WebSocketJsonConnection to be supplied
  */
trait ServerCallUtils extends  StrictLogging {


  import cats.implicits._

  /** Proxy to ServerConnection to minimize code clutter */
  def callServer(rq: JsonObject): ErrorOr[JsonReqRes] = {
    ServerConnection.doCall(rq)
  }

  def doDecodingCall[T](rq: JsonObject, decoder: Decoder[T]): Either[AppError, T] = {
    callServer(rq).flatMap(v ⇒ CirceUtils.decode(v.rs, decoder))
  }

  /** Shifts generic and engine failures to left */
  def decodeTxnCall[T](rr: JsonReqRes, decoder: Decoder[T]): Either[AppError, T] = {
    val grs: Either[AppJsonDecodingError, RippleGenericResponse] = CirceUtils.decode(rr.rs, Decoder[RippleGenericResponse])

    grs.flatMap {
      case ok: RippleGenericSuccess ⇒
        val res =  shiftEngineErrorToLeft(ok).flatMap(v⇒ CirceUtils.decode(v.result, decoder) )
        res.left.foreach { err: AppError ⇒
             logger.error(s"Txn Error: ${err.show}  \n Conversation: ${LogHelpers.reqres2string(rr)}")
        }
          res

      case bad: RippleGenericError  ⇒
        logger.debug(s"Response: ${rr.rs.spaces4}")
        logger.error(s"Bad Result $bad \nFrom\n ${rr.rq.spaces4}")
        LogHelpers.logAnswer(rr.asRight)
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
    val jsons: ErrorOr[JsonReqRes] = callServer(rq)
    val res: Either[AppError, T]   = jsons.flatMap(v => parseReqRes(v, decoder))
    (res, jsons).mapN((_, _))
  }

  /** Do the call shifting generic and txn engine errors to Left */
  def doTxnCall[T](rq: JsonObject, decoder: Decoder[T]): Either[AppError, T] = {
    callServer(rq).flatMap(parseTxnRqRs(_, decoder))
  }


  /** Do the call shifting generic and txn engine errors to Left */
  def doTxnCallKeepJson[T](rq: JsonObject, decoder: Decoder[T]): Either[AppError, (T, JsonReqRes)] = {
    for {
     rr ← callServer(rq)
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

}

object ServerCallUtils extends ServerCallUtils
