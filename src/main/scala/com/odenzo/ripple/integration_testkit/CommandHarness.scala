package com.odenzo.ripple.integration_testkit

import scala.concurrent.ExecutionContext

import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

import com.odenzo.ripple.models.support.{RippleCommand, RippleGenericError, RippleGenericResponse, RippleGenericSuccess, RippleRq, RippleRs}
import com.odenzo.ripple.utils.CirceUtils
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.utils.caterrors.{AppRippleError, OError}



class CommandHarness[A<:RippleRq,B <:RippleRs](cmd:RippleCommand[A,B], conn: WebSocketJsonConnection)  {
  def send(rq:A)(implicit ec:ExecutionContext): RippleTestCall[A, B] = {
    CommandHarness.doCommand(cmd,conn,rq)
  }
}

object CommandHarness extends StrictLogging {



  // Very laboured way of doing so we can see intermediate results with
  // special case error model objects. Special case seems better though!
  def doCommand[A <: RippleRq, B <: RippleRs](
    cmd:  RippleCommand[A, B],
    conn: WebSocketJsonConnection,
    rq:   A
  )(implicit ec: ExecutionContext) = {

    val json: Json = cmd.encode(rq)
    val rs: ErrorOr[RequestResponse[Json, Json]] = conn.ask(json)
    val generic: ErrorOr[RippleGenericResponse] = rs.flatMap(r ⇒ CirceUtils.decode(r.rs, RippleGenericResponse.decoder))
      .leftMap(err ⇒ OError("Trouble Decoding JSON Response as RippleGenericResponse", err))

    // RippleGenericSuccess or RippleGenericError or a left with Trouble Decoding JSON Response Error
    // Would be kind of nice putting the RippleGenericError into an OError and on the left side.
    val ans: ErrorOr[B] = generic.flatMap {
      case RippleGenericSuccess(_, _, result: Json) ⇒ CirceUtils.decode(result, cmd.decoder)
      case e: RippleGenericError                    ⇒ new AppRippleError("Generic Error - No Decoding of [B]", e).asLeft
    }
    RippleTestCall(rq, rs, generic, ans)
  }

}
