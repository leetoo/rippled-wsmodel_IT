package com.odenzo.ripple.apps

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

import cats.implicits._
import com.typesafe.scalalogging.StrictLogging

import com.odenzo.ripple.models.utils.caterrors.AppError
import com.odenzo.ripple.models.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.models.atoms.AccountAddr
import com.odenzo.ripple.models.support.{Codec, RippleRq, RippleRs, RippleWsNode}
import com.odenzo.ripple.testkit.comms.{RippleSender, WebSocketJsonConnection, WebSocketJsonQueueFactory}

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
  
}
