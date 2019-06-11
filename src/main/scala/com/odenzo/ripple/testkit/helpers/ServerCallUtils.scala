package com.odenzo.ripple.testkit.helpers

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, JsonObject}

import com.odenzo.ripple.models.utils.CirceUtils
import com.odenzo.ripple.models.utils.caterrors.AppError
import com.odenzo.ripple.models.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.models.wireprotocol.transactions.SubmitRs

/**
  * *LOW LEVEL* Helper utilities to send commands and transaction to Riplpe Server and parse results
  *  This demands a serverConnection:WebSocketJsonConnection to be supplied
  */
trait ServerCallUtils extends StrictLogging with DecodingHelpers {

  import cats.implicits._

  /** Proxy to ServerConnection to minimize code clutter */
  def callServer(rq: JsonObject): ErrorOr[JsonReqRes] = {
    ServerConnection.doCall(rq)
  }

 
  def doCmdCall[T](rq: JsonObject, resultDecoder: Decoder[T]): Either[AppError, T] = {
    doCmdCallKeepJson(rq, resultDecoder).map(_._1)
  }

  /** Sends the rqJson object to rippled server, stripping x=null fields.
    *  This will move any general Ripple error (success=false)  over to AppError
    * @param rq
    * @param decoder Decoder to apply to the result field of the response json.
    * @tparam T
    * @return JSON Request and Response, w/ Request stripped of null fields.
    */
  def doCmdCallKeepJson[T](rq: JsonObject, decoder: Decoder[T]): Either[AppError, (T, JsonReqRes)] = {
    val jsons: ErrorOr[JsonReqRes] = callServer(rq)
    val res: Either[AppError, T]   = jsons.flatMap(v => decodeRqRs(v, decoder))
    (res, jsons).mapN((_, _))
  }

  /** Do the call shifting generic and txn engine errors to Left */
  def doSubmitCall[T](rq: JsonObject): Either[AppError, SubmitRs] = callServer(rq).flatMap(decodeTxnRqRs)

  /** Do the call shifting generic and txn engine errors to Left */
  def doSubmitCallKeepJson[T](rq: JsonObject): Either[AppError, (SubmitRs, JsonReqRes)] = {
    for {
      rr  ← callServer(rq)
      obj ← decodeTxnRqRs(rr)
    } yield (obj, rr)
  }

}

object ServerCallUtils extends ServerCallUtils
