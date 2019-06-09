package com.odenzo.ripple.testkit.helpers

import scala.concurrent.ExecutionContextExecutor

import io.circe.JsonObject

import com.odenzo.ripple.models.utils.caterrors.AppError
import com.odenzo.ripple.models.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.models.support.RippleWsNode
import com.odenzo.ripple.models.utils.CirceUtils
import com.odenzo.ripple.testkit.comms.{WebSocketJsonConnection, WebSocketJsonQueueFactory}

/** Single place to create connections and handle preferences, error handling etc.
  * Migrating to only use server in stand-alone mode.
  **/
object ServerConnection {
  implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  val localSA: RippleWsNode = RippleWsNode("LocalSA", "ws://127.0.0.1:6006", true)

  /** Fail fast if connection cannot be made (will timeout usually) */
  val con = {
    connect(localSA) match {
      case Left(err)  ⇒ throw new IllegalStateException(s"Could not connect to $localSA : $err")
      case Right(con) ⇒ con
    }

  }

  def connect(node: RippleWsNode): Either[AppError, WebSocketJsonConnection] = {
    for {
      queue ← new WebSocketJsonQueueFactory(node).connect()
    } yield queue
  }

  def doCall(rq: JsonObject): ErrorOr[JsonReqRes] = {
    // Serializer does this too, but needed for clean fixture generation
    val cleanRq = CirceUtils.pruneNullFields(rq)
    con.ask(cleanRq)
  }

}
