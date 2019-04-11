package com.odenzo.ripple.apps

import scala.concurrent.ExecutionContext

import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

import com.odenzo.ripple.apps.ProdBalances.{CurrenciesForAccount, scroll}
import com.odenzo.ripple.integration_testkit.{OtherTestCommandHarness, RequestResponse, RippleSender, TestCallResults}
import com.odenzo.ripple.models.atoms.{AccountAddr, Currency, CurrencyAmount, Drops, FiatAmount, Script, TrustLine, TxnSequence}
import com.odenzo.ripple.models.support.{Codec, Commands, RippleAnswer, RippleGenericError, RippleRq, RippleRs}
import com.odenzo.ripple.models.wireprotocol.accountinfo.{AccountCurrenciesRq, AccountCurrenciesRs, AccountInfoRq, AccountInfoRs, AccountLinesRq, AccountLinesRs}
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.utils.caterrors.{AppError, AppRippleError}

/**
  * Playing around app to fetch some balances from RIppled in production/public.
  * This should be wrapped up as generic call (let them pass in ledger and account).
  */
object ProdBalances extends StrictLogging {
  val defaultAccount: AccountAddr = AccountAddr("rLqxc4bxqRVVt63kHVk22Npmq9cqHVdyR")

  def checkBalances(
      account: AccountAddr = defaultAccount
  )(implicit context: RippleComContext): Either[AppError, List[CurrencyAmount]] = {

    implicit val sender: RippleSender = context.con

    val answer: Either[AppError, List[CurrencyAmount]] = for {
      xrp  <- BusinessCalls.xrpBalanceViaAccountInfo(defaultAccount)
      fiat <- allFiatBalances(defaultAccount)

    } yield xrp :: fiat

    answer.raiseOrPure

    answer.foreach { l: List[CurrencyAmount] ⇒
      val lByl = l.map(_.show).mkString("Balances:\n\t", "\n\t", "\n\n")
      logger.info(lByl)
    }
    answer
  }

  /**
    * This isn't tail recursibe which is ok, but it is also not well tested.
    * This is prototype to build a more generalized framework based on RippleScrollableRq/Rs
    *
    * @param rq
    * @tparam A
    * @tparam B
    *
    * @return
    */
  def scroll[A <: AccountLinesRq, B <: AccountLinesRs](
      rq: AccountLinesRq
  )(implicit context: RippleComContext): ErrorOr[List[AccountLinesRs]] = {
    val accountLines                                         = new OtherTestCommandHarness(Commands.accountLinesCmd, context.con)
    val raw: TestCallResults[AccountLinesRq, AccountLinesRs] = accountLines.send(rq)(context.ec)
    val rs: ErrorOr[AccountLinesRs]                          = raw.result

    raw.json.foreach { rr ⇒
      logger.info("Scrolling JSON:" + rr.show)
    }

    rs.flatMap { line: AccountLinesRs ⇒
      line.marker match {
        case None ⇒ // No More Scrolling Needed
          val res: ErrorOr[List[AccountLinesRs]] = (line :: Nil).asRight[AppError]
          res
        case Some(marker: Json) ⇒ // If there is a marker then we need to update the request and do another call
          val nextRq: AccountLinesRq = rq.copy(marker = Some(marker))
          val recurse                = scroll(nextRq)
          recurse
      } // match
    } //  rs.flatMap
  } // scroll function

  /**
    * Note: Same currency by multiple issuers will be returned as seperate balances
    *
    * @param account
    *
    * @return All the non-XRP account balances, including zero balances. By (currency,issuer)
    */
  def allFiatBalances(account: AccountAddr)(implicit rcc: RippleComContext): Either[AppError, List[FiatAmount]] = {
    val callResults = BusinessCalls.allAccountLines(account).map { l: List[AccountLinesRs] ⇒
      l.flatMap(rs ⇒ rs.lines)
        .map(balanceFromTrustLine)
    }
    callResults
  }

  /**
    * @param trust A trustline, typically from AccountLineRs
    *
    * @return Repacked into a FiatAmount Wrapper, throwing away some information.
    */
  def balanceFromTrustLine(trust: TrustLine): FiatAmount = {
    val script: Script      = Script(trust.currency, trust.account) // Currency and Issuer
    val balance: FiatAmount = FiatAmount(trust.balance, script)
    balance // CurrencyAmount vs FiatAmount

  }

  case class CurrenciesForAccount(send: List[Currency], receive: List[Currency]) {
    def all: List[Currency] = (send ++ receive).distinct
  }

}

object BusinessCalls extends StrictLogging {

  def prodCall[A <: RippleRq, B <: RippleRs](cmd: Codec[A, B],
                                             rq: A,
                                             context: RippleComContext): ErrorOr[RippleAnswer[B]] = {
    prodCall(cmd, rq)(context.con, context.ec)
  }

  def prodCall[A <: RippleRq, B <: RippleRs](
      cmd: Codec[A, B],
      rq: A
  )(implicit comm: RippleSender, ec: ExecutionContext): ErrorOr[RippleAnswer[B]] = {
    val rqJson: Json                                      = cmd.encode(rq)
    val callResults: ErrorOr[RequestResponse[Json, Json]] = comm.ask(rqJson)
    val rs: Either[AppError, RippleAnswer[B]]             = callResults.flatMap(rr ⇒ cmd.decode(rr.rs))
    rs
  }

  /**
  *   This lifts RippleGenericError to top level error. Unfortunately, on success it throws away the msg id.
    *   
    * @param v
    * @tparam B
    * @return
    */
  def liftRippleAnswerError[B<:RippleRs](v: ErrorOr[RippleAnswer[B]]): ErrorOr[B] = {
    v.flatMap { ans: RippleAnswer[B] ⇒
      ans match {
      case RippleAnswer(id,msg,Left(rge))  ⇒ new AppRippleError(s"Ripple Generic Error for $id", rge).asLeft
      case RippleAnswer(id,msg,Right(r)) ⇒ r.asRight
    }
  }
  }

  def liftRippleError[B](v: ErrorOr[Either[RippleGenericError, B]]): ErrorOr[B] = {
    v.flatMap{
      case Left(rge)  ⇒ new AppRippleError("Ripple Generic Error", rge).asLeft
      case Right(ans) ⇒ ans.asRight
    }
  }


  def checkAccountCurrencies(account: AccountAddr)(implicit com: RippleComContext): ErrorOr[CurrenciesForAccount] = {

    def postProcess(rs: AccountCurrenciesRs): CurrenciesForAccount = {
      CurrenciesForAccount(send = rs.send_currencies, receive = rs.receive_currencies)
    }


    val cmd: Codec[AccountCurrenciesRq, AccountCurrenciesRs]   = Commands.accountCurrenciesCmd
    val rq: AccountCurrenciesRq                                = AccountCurrenciesRq(account)
    val call: ErrorOr[RippleAnswer[AccountCurrenciesRs]] = prodCall(cmd, rq, com)
    val done: ErrorOr[Either[RippleGenericError, CurrenciesForAccount]] = call.map { answer ⇒
      answer.ans.map(postProcess)
    }

    liftRippleError(done)

    // or can lift earlier in this case. Perhaps a Functor / Nested approach is useful
    //val ok: ErrorOr[AccountCurrenciesRs] = liftRippleError(callResult)
    //ok.map(postProcess)


  }

  /**
    * Gets the current account sequence number of current ripple server fee via accountinfo
    * This is very common call, and may be optimized by limiting AccountInfoRs
    * @param account
    *
    * @return ErrorOr the account balance for given account in Drops (XRP Balance)
    */
  def signingInfo(account: AccountAddr)(implicit context: RippleComContext): Either[AppError, TxnSequence] = {
    val accountInfo = Commands.accountInfoCmd
    val harness = new OtherTestCommandHarness(accountInfo, context.con)
    val rq = AccountInfoRq(account)
    val rs: TestCallResults[AccountInfoRq, AccountInfoRs] = harness.send(rq)(context.ec)
    val txnSequence: Either[AppError, TxnSequence] = rs.result.map(v ⇒ v.account_data.sequence)
    txnSequence.foreach((d: TxnSequence) ⇒ logger.info(s"Txn Sequence ${account.show}  = ${d.toString}"))
    txnSequence
  }

  /**
    * Get the XRP balance for given account at current validatred ledger.
    * This is best way to go.
    *
    * @param account
    *
    * @return ErrorOr the account balance for given account in Drops (XRP Balance)
    */
  def xrpBalanceViaAccountInfo(account: AccountAddr)(implicit context: RippleComContext): Either[AppError, Drops] = {
    val accountInfo                                       = Commands.accountInfoCmd
    val harness                                           = new OtherTestCommandHarness(accountInfo, context.con)
    val rq                                                = AccountInfoRq(account)
    val rs: TestCallResults[AccountInfoRq, AccountInfoRs] = harness.send(rq)(context.ec)
    val balanceInDrops: Either[AppError, Drops]           = rs.result.map(v ⇒ v.account_data.balance)
    balanceInDrops.foreach(d ⇒ logger.info(s"XRP Balance = ${Drops.formatAsXrp(d)}"))
    balanceInDrops
  }

  /** Get all the account balances for currencies the account has trust lines for .
    * This is a manual protottpe for scrollable results. Includes zero balances.
    * */
  def allAccountLines(
      account: AccountAddr
  )(implicit context: RippleComContext): Either[AppError, List[AccountLinesRs]] = {
    val accountLines                          = new OtherTestCommandHarness(Commands.accountLinesCmd, context.con)
    val rq: AccountLinesRq                    = AccountLinesRq(account, limit = 5)
    val answer: ErrorOr[List[AccountLinesRs]] = scroll(rq)
    answer
  }
}
