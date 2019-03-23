package com.odenzo.ripple.apps
import scala.concurrent.ExecutionContextExecutor

import com.typesafe.scalalogging.StrictLogging

import com.odenzo.ripple.integration_testkit.{TestCallResults, TestCommandHarness, WebSocketJsonConnection, WebSocketJsonQueueFactory}
import com.odenzo.ripple.models.support.{RippleCommand, RippleRq, RippleWsNode}
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr
import cats._
import cats.data._
import cats.implicits._

import com.odenzo.ripple.models.atoms.{Account, AccountAddr, Currency, Drops}
import com.odenzo.ripple.models.utils.AccountBalance
import com.odenzo.ripple.models.wireprotocol.accountinfo.{AccountCurrenciesRq, AccountCurrenciesRs, AccountInfoRq, AccountInfoRs, AccountLinesRq}
import com.odenzo.ripple.utils.caterrors.AppError

/**
  * Playing around app to fetch some balances from RIppled in production/public
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

  checkXrpBalance(account)

  def checkXrpBalance(account: AccountAddr) = {



    import com.odenzo.ripple.models.support.Commands.AccountInfo.AccountCurrenciesCmd
    import com.odenzo.ripple.models.support.Commands.AccountInfo.AccountInfoCmd
    import com.odenzo.ripple.models.support.Commands.AccountInfo.AccountLinesCmd

    val accountCurrencies =new TestCommandHarness(AccountCurrenciesCmd, con)
    val accountInfo = new TestCommandHarness(AccountInfoCmd,con)
    val accountLines = new TestCommandHarness(AccountLinesCmd, con)

    val rq = AccountCurrenciesRq(account)
    val callResults = accountCurrencies.send(rq)
    logger.info("Results: " + TestCallResults.dump(callResults))
    val cc = callResults.result.map(r ⇒ (r.receive_currencies ++ r.send_currencies).distinct)
    cc.foreach(l⇒ logger.info("Currencies: " + l.map(_.show).mkString(" : ")))

    val infoRq = AccountInfoRq(account)
    val rs = accountInfo.send(infoRq)
    val infoRs: ErrorOr[AccountInfoRs] = rs.result
    logger.info("Results: " + rs.showMe())
      val balanceInDrops = infoRs.map(_.account_data.balance)
    balanceInDrops.foreach(d⇒logger.info(s"XRP Balance = ${Drops.formatAsXrp(d)}"))


    // Account Lines is a Scrollable Result set.
    val alr = accountLines.send(AccountLinesRq(account))
    logger.info("Results: " + alr.showMe())

    val lines  = alr.result
    lines.foreach (v⇒logger.info("Accoubt Lines" + v))


  }

}
