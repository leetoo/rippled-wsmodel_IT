package com.odenzo.ripple.integration_testkit.subscriptions

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Supervision}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

import com.odenzo.ripple.integration_testkit.FlowSegments
import com.odenzo.ripple.integration_testkit.subscriptions.SubscribeWebSocket.{ACK, COMPLETE, INITIALIZE, SubscribeRs}
import com.odenzo.ripple.models.support.RippleWsNode
import com.odenzo.ripple.localops.utils.caterrors.CatsTransformers.ErrorOr

/**
  * Subscriptions works by sending a subcribe over websocket and all the results come back
  * over that websocket. First message is response message, rest are data.
  * None of the messages are checked for correctness, just that they are parseable json.
  * Details At: https://ripple.com/build/rippled-apis/#subscribe
  *
  * Every message sent needs to be replied to by the receiver actor using SubscribeWebSocket.ACK
  * Note that subscriptions have varying communication patterns depending on subscribe request.
  *
  * This has a Sink of ActorRefWithAck which seems to work ok.
  * TODO: Proper closing and error control.
  *
  * @param rippleNode The rippled server to connect to, this can be secondary node in many cases.
  * @param rq         The subscribe request that will trigger a stream of "responses"/events.
  * @param receiver   Business Actor that will receive each event. Must supply ACK for
  *                   backpressure based flow control. SubscriotionActor will act as a Facade know, delegating to a
  *                   business actor just the SubscribeRs messages.
  *
  */
class SubscribeWebSocket(val rippleNode: RippleWsNode, val rq: Json, val receiver: ActorRef)(
    implicit val system: ActorSystem
) extends FlowSegments
    with StrictLogging {

  val decider: Supervision.Decider = {
    case _: ArithmeticException => Supervision.Resume
    case _                      => Supervision.Stop
  }

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  import system.dispatcher

  /**
    * In this case the back-pressure is kind of meaningless.
    */
  val actorAckSink: Sink[SubscribeRs, NotUsed] = {
    Sink.actorRefWithAck(receiver, INITIALIZE, ACK, COMPLETE)
  }

  subscribe(rippleNode, rq, actorAckSink, logInOutMsg = true)

  /**
    * This opens a new websocket, sends a message and waits for one reply.
    * Throws exception is a JSon response not received, but no checking on semantic errors from Rippled in response.
    * The sink that receovices all
    *
    * @param dest Which Ripple Server and WebSocket port to connect to
    * @param rq JsonRq -- not this version expects an id element already and doesn't preprocess message
    * @param logInOutMsg log the request and response messages  at info instead of trace
    *
    * @return
    */
  private def subscribe(dest: RippleWsNode,
                        rq: Json,
                        monitor: Sink[SubscribeRs, NotUsed],
                        logInOutMsg: Boolean = true): Unit = {
    logger.info("Creating Subscribe Connection to: " + dest.url)

    val fullStream = Source
      .single(rq)
      .concatMat(Source.maybe[Json])(Keep.right) // Needed maybe to keep stream open
      .via(jsonToMessage)
      .viaMat(createConfiguredSocket(dest.url))(Keep.both) // Json Rq => Json Responses  (both as Message 's)
      .via(messageToString)
      .via(stringToJson)
      .map(v => SubscribeWebSocket.SubscribeRs(v))
      .toMat(actorAckSink)(Keep.both)

    val ((closer, upgraded), sinkOut) = fullStream.run()

    upgraded.failed.foreach(e => logger.error("Connect/Upgrade of WebSocket Failed", e))
    upgraded.foreach(m => logger.trace(s"Connect/Upgrade of WebSocket SUCCESS - $m"))

  }

  // To delegate to an actor with backpressure use:  .mapAsync(parallelism = 5)(elem => ask(receiver, elem).mapTo[String]) // Ask/? pattern for backpressure.

}

object SubscribeWebSocket {

  /**
    * In Ripple context there are two kinds of messages. If an Error it is at transport level, not Ripple level.
    * @param v
    */
  case class SubscribeRs(v: ErrorOr[Json])

  case object INITIALIZE

  case object ACK

  case object COMPLETE

}
