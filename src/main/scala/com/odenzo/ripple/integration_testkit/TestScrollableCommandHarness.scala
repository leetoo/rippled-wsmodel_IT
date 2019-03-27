package com.odenzo.ripple.integration_testkit

import scala.concurrent.ExecutionContext

import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

import com.odenzo.ripple.models.support.{RippleCommand, RippleGenericError, RippleGenericResponse, RippleGenericSuccess, RippleRq, RippleRs}
import com.odenzo.ripple.utils.CirceUtils
import com.odenzo.ripple.utils.caterrors.{AppError, AppException, AppRippleError, OError}
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr

/**
*  List hack harness to use with Akka HTTP and Akka Streams approach to a command accross.
  *  Doesn't handle taking care of a transaction flow yet.
  *
  *  I use this instead of the CommandHarness directly to deal with (IntelliJ or Scala?) type
  *  inference problems using
  *  {{
  *   import com.odenzo.ripple.models.support.Commands.AccountInfo.AccountCurrenciesCmd
  *
  *     // Works
  *     val rs: TestCallResults[AccountCurrenciesRq, AccountCurrenciesRs] = CommandHarness.doCommand(AccountCurrenciesCmd, con, rq)
  *
  *     // Doesn't work
  *     val accountCurrencies: A ⇒ TestCallResults[RippleRq, AccountCurrenciesRs] = CommandHarness.doCommand(AccountCurrenciesCmd, con, _)
  *
  *     // Works  but type is too wide.
      * val accountCurrencies
  *     : (RippleCommand[Nothing, Nothing], WebSocketJsonConnection, Nothing) ⇒ TestCallResults[Nothing, Nothing]  = CommandHarness.doCommand
  *     (AccountCurrenciesCmd, con, _:AccountCurrenciesRq)
  *
  *  }}
  *
  * @param cmd
  * @param conn
  * @tparam A
  * @tparam B
  */
class TestScrollableCommandHarness[A<:RippleRq,B <:RippleRs](cmd:RippleCommand[A,B],
                                                                         conn: WebSocketJsonConnection)  {

  def bind()(implicit executionContext: ExecutionContext): A ⇒ TestCallResults[A, B] = TestCommandHarness.doCommand(cmd, conn, _)

  
  def send(rq:A)(implicit ec:ExecutionContext): TestCallResults[A, B] = {
    TestCommandHarness.doCommand(cmd, conn, rq)
  }
}

object TestScrollableCommandHarness extends StrictLogging {

  // Very laboured way of doing so we can see intermediate results with
  // special case error model objects. Special case seems better though!
  def doCommand[A <: RippleRq, B <: RippleRs](
      cmd: RippleCommand[A, B],
      conn: WebSocketJsonConnection,
      rq: A
  )(implicit ec: ExecutionContext) = {

    import cats._
    import cats.data._
    import cats.implicits._
    
    val json: Json                               = cmd.encode(rq)
    val rs: ErrorOr[RequestResponse[Json, Json]] = conn.ask(json)
    val generic: Either[AppError, RippleGenericResponse] = rs
                                                           .flatMap(r ⇒ CirceUtils.decode(r.rs, RippleGenericResponse.decoder))

    
    // RippleGenericSuccess or RippleGenericError or a left with Trouble Decoding JSON Response Error
    // Would be kind of nice putting the RippleGenericError into an OError and on the left side.
    val ans: ErrorOr[B] = generic.flatMap {
      case RippleGenericSuccess(_, _, result: Json) ⇒ CirceUtils.decode(result, cmd.decoder)
      case e: RippleGenericError                    ⇒ new AppRippleError("Generic Error - No Decoding of [B]", e).asLeft
    }
    TestCallResults(rq, rs, generic, ans)
  }

}
