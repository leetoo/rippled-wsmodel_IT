package com.odenzo.ripple.testkit.helpers

import io.circe.Decoder

import com.odenzo.ripple.models.support.{RippleEngineResult, RippleGenericError, RippleGenericResponse, RippleGenericSuccess}
import com.odenzo.ripple.models.utils.CirceUtils
import com.odenzo.ripple.models.utils.caterrors.{AppError, AppJsonDecodingError, OError}
import com.odenzo.ripple.models.wireprotocol.transactions.SubmitRs
import cats._
import cats.data._
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging

/**
* Some helper functions for decoding the Json responses from ripple server
  * and interpreting the error responses (generic and engine errors)
  */
trait DecodingHelpers extends StrictLogging {

  /** Filters out success = false generic errors */
  def decodeRqRs[T](rr: JsonReqRes, decoder: Decoder[T]): Either[AppError, T] = {
    CirceUtils
    .decode(rr.rs, Decoder[RippleGenericResponse])
    .flatMap{
      case ok: RippleGenericSuccess ⇒ CirceUtils.decode(ok.result, decoder)
      case bad: RippleGenericError  ⇒
        logger.debug(s"Response: ${rr.rs.spaces4}")
        logger.error(s"Bad Result $bad \nFrom\n ${rr.rq.spaces4}")
        AppError("Failed ").asLeft
    }
  }

  def decodeTxnRqRs(rr: JsonReqRes): Either[AppError, SubmitRs] = {
    val decoder = Decoder[SubmitRs]
    CirceUtils
    .decode(rr.rs, Decoder[RippleGenericResponse])
    .flatMap(shiftGenericErrorsToLeft)
    .flatMap((v: RippleGenericSuccess) => shiftEngineErrorToLeft(v))
    .flatMap(success => CirceUtils.decode(success.result, decoder))

  }

  /** Shifts generic and engine failures to left */
  def decodeTxnCall[T](rr: JsonReqRes, decoder: Decoder[T]): Either[AppError, T] = {
    val grs: Either[AppJsonDecodingError, RippleGenericResponse] =
      CirceUtils.decode(rr.rs, Decoder[RippleGenericResponse])

    grs.flatMap{
      case ok: RippleGenericSuccess ⇒
        val res = shiftEngineErrorToLeft(ok).flatMap(v ⇒ CirceUtils.decode(v.result, decoder))
        res.left.foreach{ err: AppError ⇒
          logger.error(s"Txn Error: ${err.show}  \n Conversation: ${LogHelpers.reqres2string(rr)}")
        }
        res

      case bad: RippleGenericError ⇒
        logger.debug(s"Response: ${rr.rs.spaces4}")
        logger.error(s"Bad Result $bad \nFrom\n ${rr.rq.spaces4}")
        LogHelpers.logAnswer(rr.asRight)
        AppError("Failed Transaction").asLeft
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
    engine.flatMap{ er =>
      if (er.isSuccess) success.asRight
      else new OError(s"Engine Failure ${er.engine_result_message} " + er).asLeft
    }
  }
}

object DecodingHelpers extends DecodingHelpers
