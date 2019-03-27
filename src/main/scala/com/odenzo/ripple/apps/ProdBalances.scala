package com.odenzo.ripple.apps
import scala.concurrent.ExecutionContextExecutor

import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

import com.odenzo.ripple.integration_testkit.{
  TestCallResults,
  TestCommandHarness,
  WebSocketJsonConnection,
  WebSocketJsonQueueFactory
}
import com.odenzo.ripple.models.atoms.{AccountAddr, CurrencyAmount, Drops, FiatAmount, Script, TrustLine}
import com.odenzo.ripple.models.support.Commands.AccountInfo.{AccountInfoCmd, AccountLinesCmd}
import com.odenzo.ripple.models.support.RippleWsNode
import com.odenzo.ripple.models.wireprotocol.accountinfo.{
  AccountCurrenciesRq,
  AccountInfoRq,
  AccountInfoRs,
  AccountLinesRq,
  AccountLinesRs
}
import com.odenzo.ripple.utils.caterrors.AppError
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr

/**
  * Playing around app to fetch some balances from RIppled in production/public.
  * This should be wrapped up as generic call (let them pass in ledger and account).
  */
object ProdBalances extends App with StrictLogging {

  implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  val ripplenet: RippleWsNode                       = RippleWsNode("Prod", "wss://s2.ripple.com:443", false)
  val factory: WebSocketJsonQueueFactory            = new WebSocketJsonQueueFactory(ripplenet, true)
  val connAttempt: ErrorOr[WebSocketJsonConnection] = factory.connect()

  val account = AccountAddr("rLqxc4bxqRVVt63kHVk22Npmq9cqHVdyR")

  connAttempt.left.foreach { err: AppError ⇒
    logger.error(s"Trouble Connecting to $ripplenet", err)
    logger.error(s"Showing: ${err.show}")
  }

  val con = connAttempt.getOrElse(throw new IllegalStateException("Can't continue without a connection."))

  val answer: Either[AppError, List[CurrencyAmount]] = for {
    xrp  <- xrpBalance(account)
    fiat <- allFiatBalances(account)

  } yield xrp :: fiat

  answer.left.foreach(ae ⇒ logger.error("Error: " + ae.show))

  answer.foreach { l: List[CurrencyAmount] ⇒
    val lByl = l.map(_.show).mkString("Balances:\n\t", "\n\t", "\n\n")
    logger.info(lByl)
  }

  def checkXrpBalance(account: AccountAddr) = {

    import com.odenzo.ripple.models.support.Commands.AccountInfo.AccountCurrenciesCmd

    val accountCurrencies = new TestCommandHarness(AccountCurrenciesCmd, con)

    val rq          = AccountCurrenciesRq(account)
    val callResults = accountCurrencies.send(rq)
    logger.info("Results: " + TestCallResults.dump(callResults))
    val cc = callResults.result.map(r ⇒ (r.receive_currencies ++ r.send_currencies).distinct)
    cc.foreach(l ⇒ logger.info("Currencies: " + l.map(_.show).mkString(" : ")))
    cc
  }

  /**
    * Get the XRP balance for given account at current validatred ledger.
    *
    * @param account
    *
    * @return ErrorOr the account balance for given account in Drops (XRP Balance)
    */
  def xrpBalance(account: AccountAddr): Either[AppError, Drops] = {
    val accountInfo                                       = new TestCommandHarness(AccountInfoCmd, con)
    val rq                                                = AccountInfoRq(account)
    val rs: TestCallResults[AccountInfoRq, AccountInfoRs] = accountInfo.send(rq)
    val balanceInDrops: Either[AppError, Drops]           = rs.result.map(v ⇒ v.account_data.balance)
    balanceInDrops.foreach(d ⇒ logger.info(s"XRP Balance = ${Drops.formatAsXrp(d)}"))
    balanceInDrops
  }

  /** Get all the account balances for currencies the account has trust lines for .
    * This is a manual protottpe for scrollable results. Includes zero balances.
    **/
  def allAccountLines(account: AccountAddr): Either[AppError, List[AccountLinesRs]] = {
    val accountLines                          = new TestCommandHarness(AccountLinesCmd, con)
    val rq: AccountLinesRq                    = AccountLinesRq(account, limit=5)
    val answer: ErrorOr[List[AccountLinesRs]] = scroll(rq)
    answer
  }

  /**
  *  This isn't tail recursibe which is ok, but it is also not well tested.
    *  This is prototype to build a more generalized framework based on RippleScrollableRq/Rs
    * @param rq
    * @tparam A
    * @tparam B
    * @return
    */
  def scroll[A <: AccountLinesRq, B <: AccountLinesRs](rq: AccountLinesRq): ErrorOr[List[AccountLinesRs]] = {
    val accountLines                                         = new TestCommandHarness(AccountLinesCmd, con)
    val raw: TestCallResults[AccountLinesRq, AccountLinesRs] = accountLines.send(rq)
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
    *  Note: Same currency by multiple issuers will be returned as seperate balances
    *
    * @param account
    *
    *                @return All the non-XRP account balances, including zero balances. By (currency,issuer)
    */
  def allFiatBalances(account: AccountAddr): Either[AppError, List[FiatAmount]] = {
    val callResults = allAccountLines(account).map { l: List[AccountLinesRs] ⇒
        l.flatMap(rs ⇒ rs.lines)
        .map(balanceFromTrustLine)
    }
    callResults
  }

  /**
    *  Given a ripple AccountLinesRs message, extracts the balances out.
    * Is there a reason not to put in AccountLinesRs ?
    *
    * @param rs
    *
    * @return
    */
  def balanceFromTrustLine(trust: TrustLine): FiatAmount = {
    val script: Script      = Script(trust.currency, trust.account) // Currency and Issuer
    val balance: FiatAmount = FiatAmount(trust.balance, script)
    balance // CurrencyAmount vs FiatAmount

  }
}
