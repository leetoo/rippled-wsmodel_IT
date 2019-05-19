package com.odenzo.ripple.integration_testkit

import scala.concurrent.ExecutionContext

import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

import com.odenzo.ripple.models.support.{Codec, RippleGenericError, RippleGenericResponse, RippleGenericSuccess, RippleRq, RippleRs, RippleScrollingRq, RippleScrollingRs}
import com.odenzo.ripple.localops.utils.CirceUtils
import com.odenzo.ripple.localops.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.localops.utils.caterrors.{AppRippleError, OError}


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
class TestCommandHarness[A<:RippleRq,B <:RippleRs](cmd:Codec[A,B], conn: RippleSender)  {

  def bind()(implicit executionContext: ExecutionContext): A ⇒ TestCallResults[A, B] = TestCommandHarness.doCommand(cmd, conn, _)

  
  def send(rq:A)(implicit ec:ExecutionContext): TestCallResults[A, B] = {
    TestCommandHarness.doCommand(cmd, conn, rq)
  }
}

object TestCommandHarness extends StrictLogging {


  // Very laboured way of doing so we can see intermediate results with
  // special case error model objects. Special case seems better though!
  def doCommand[A <: RippleRq, B <: RippleRs](
                                               cmd:  Codec[A, B],
                                               conn: RippleSender,
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
    TestCallResults(rq, rs, generic, ans)
  }


  /**
  * Battline Scala on applying Type stuff. Not sure how to ensure that RippleScrolling{Rq,Rs} is a type class
    * @param cmd
    * @param conn
    * @param rq
    * @param fn
    * @param ec
    * @tparam A
    * @tparam B
    * @return
    */
  def doScrollingAll[A<: RippleScrollingRq, B<: RippleScrollingRs](
                                                                    cmd:  Codec[A, B],
                                                                    conn: WebSocketJsonConnection,
                                                                    rq:   A,
                                                                    fn: (A,B) ⇒ A, // Scrolling Function
                                                                  )(implicit ec: ExecutionContext)
  :List[TestCallResults[A,B]] = {

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

    val tcr: TestCallResults[A, B] = TestCallResults(rq, rs, generic, ans)

    val theList: List[TestCallResults[A, B]]  = tcr.result match {
      case Left(err) ⇒  tcr::Nil // Finish on an error
      case Right(rs) if rs.marker.isEmpty ⇒ tcr::Nil  // Or no scrolling left
      case Right(rs) ⇒
        val updatedRq = fn(rq, rs)
        tcr :: doScrollingAll(cmd,conn, updatedRq,fn)
  

    }
    theList
  }
}
