package com.odenzo.ripple.CommonFunctions

import com.odenzo.ripple.integration_testkit.{WebSocketJsonConnection, WebSocketJsonQueueFactory}
import com.odenzo.ripple.models.support.RippleWsNode
import com.odenzo.ripple.localops.utils.caterrors.CatsTransformers.ErrorOr

object SharedRippleConnection {

  val conn: ErrorOr[WebSocketJsonConnection] = init()

  def init(): ErrorOr[WebSocketJsonConnection] = {
    val node = RippleWsNode("LocalTest", "ws://localhost:5005", true)
    val conn= new WebSocketJsonQueueFactory(node).connect()
    conn
  }
}
