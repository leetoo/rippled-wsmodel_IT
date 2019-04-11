package com.odenzo.ripple.apps

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

import akka.Done
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder}

import com.odenzo.ripple.integration_testkit.{RippleSender, WebSocketJsonConnection, WebSocketJsonQueueFactory}
import com.odenzo.ripple.models.atoms.{AccountAddr, CurrencyAmount}
import com.odenzo.ripple.models.support.{Codec, RippleRq, RippleRs, RippleWsNode}
import com.odenzo.ripple.models.wireprotocol.accountinfo.{AccountLinesRq, AccountLinesRs}
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.utils.caterrors.{AppError, AppRippleError}

/** We should actually put this in the RippleModels section with a request object having a default codec.
  *   Default because sometimes don't need to decode the final results and can make more efficient RippleRs subclass.
  *
**/
case class RippleComContext(ec: ExecutionContext, con: RippleSender)

case class RippleContext[A <: RippleRq, B <: RippleRs](coms: RippleComContext, codec: Codec[A, B])

object MainApp extends App with StrictLogging {

  val defaultAccount: AccountAddr           = AccountAddr("rLqxc4bxqRVVt63kHVk22Npmq9cqHVdyR")
  implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  val ripplenet: RippleWsNode               = RippleWsNode("Prod", "wss://s2.ripple.com:443", false)

  val factory: WebSocketJsonQueueFactory = new WebSocketJsonQueueFactory(ripplenet, true)

  val connAttempt: ErrorOr[WebSocketJsonConnection] = {
    val con = factory.connect()
    connAttempt.left.foreach { err: AppError ⇒
      logger.error(s"Trouble Connecting to $ripplenet", err)
      logger.error(s"Showing: ${err.show}")
    }
    con
  }
  
  val wsSender: WebSocketJsonConnection = connAttempt
    .getOrElse(throw new IllegalStateException("Can't continue without a connection."))
  implicit val con: RippleSender = wsSender

  // Three usual contexts:   The command, the RippleSender, and an execution context.
  // F[A] -- Encoder is a typeclass
  val encoder: Encoder[AccountLinesRq] = Encoder[AccountLinesRq]
  // H[B]
  val decoder: Decoder[AccountLinesRs] = Decoder[AccountLinesRs]

  val theCodec: Codec[AccountLinesRq, AccountLinesRs] = Codec(Encoder[AccountLinesRq], Decoder[AccountLinesRs])

  implicit val context: RippleComContext = RippleComContext(ExecutionContext.global, con)

  // How do we go to combining these

  /**
    * So the needful. Retrun true for fraemwork to call shutdown immediately.
    * Else, expectation is action will call shutdown when it thinks its all done as a main.
    *
    * @return
    */
  def action(): Boolean = {

    // Inside here call you stuff, when complete I will close down the universe.
    val balances: Either[AppError, List[CurrencyAmount]] = ProdBalances.checkBalances(defaultAccount)

    true

  }

  def shutdown(): Unit = {
    val waitingToDie: Either[AppError, Future[Done]] = connAttempt.map(_.shutdown())
    waitingToDie.foreach(future ⇒ Await.result(future, FiniteDuration(2, "minutes")))

  }

  if (action()) shutdown()

  def lameErrorHandling(ae: AppError): Unit = {
    logger.error("Error: " + ae.show)

    logger.error("Error ToString: " + AppError.summary(ae))

    ae match {
      case a: AppRippleError[_] ⇒ logger.info("AppRippleError type erased: " + a.obj)
      case other                ⇒ logger.info("Error Type " + other.getClass.toGenericString)
    }
  }

  case class RRTypes[A <: RippleRq, B <: RippleRs]()

}
