package com.odenzo.ripple.integration_testkit

import scala.concurrent.ExecutionContext

import com.odenzo.ripple.models.support.{Codec, RippleRq, RippleRs}


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
class OtherTestCommandHarness[A<:RippleRq,B <:RippleRs](cmd:Codec[A,B], conn: RippleSender)  {

  def bind()(implicit executionContext: ExecutionContext): A ⇒ TestCallResults[A, B] = TestCommandHarness.doCommand(cmd, conn, _)

  
  def send(rq:A)(implicit ec:ExecutionContext): TestCallResults[A, B] = {
    TestCommandHarness.doCommand(cmd, conn, rq)
  }
}


