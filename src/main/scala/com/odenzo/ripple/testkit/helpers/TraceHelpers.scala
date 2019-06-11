package com.odenzo.ripple.testkit.helpers

import com.odenzo.ripple.models.utils.CirceUtils
import com.odenzo.ripple.models.utils.caterrors.AppError
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRq, SignRs, SubmitRq}
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.RippleTransaction
import io.circe.syntax._

import com.odenzo.ripple.models.atoms.AccountKeys

/** A major use is generating test cases, this helps build traces
  * Would like to make a DSL or pure functional so can build scenerios and execute later in a context.
  */
trait TraceHelpers {

  /**
    *
    * @param cmd
    * @tparam T The Type of Decoder to apply to the response
    *
    * @return
    */
  def submit[T <: RippleTransaction](signed: SignRs): Either[AppError, Any] = {

    val submitRq = SubmitRq(signed.tx_blob, fail_hard = true)
    for {
      submitRes ← ServerCallUtils.doSubmitCallKeepJson(submitRq.asJsonObject)
    } yield TracedRes(submitRes._1, submitRes._2)

  }

  /** Signs on the server with full tracing */
  def signOnServer[T <: RippleTransaction](tx: T, keys: AccountKeys): Either[AppError, TracedRes[SignRs]] = {
    val txJson       = RippleTransaction.encoder.apply(tx)
    for {
      txjson ← CirceUtils.json2jsonobject(txJson)
      toSign = SignRq(txjson.asJson, keys.master_seed, false, key_type = keys.key_type.v)
      signed ← ServerCallUtils.doCmdCallKeepJson(toSign.asJsonObject, SignRs.decoder2)
    } yield TracedRes(signed._1, signed._2)
  }

}

object TraceHelpers extends TraceHelpers
