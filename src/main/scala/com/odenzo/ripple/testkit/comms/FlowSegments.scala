package com.odenzo.ripple.testkit.comms

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl.{Flow, Keep, Sink, SinkQueueWithCancel, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

import com.odenzo.ripple.models.utils.CirceUtils
import com.odenzo.ripple.models.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.models.utils.caterrors.OError

/** Common flow pieces used/assembled into specific end-to-end flows.
  *  These are Akka flows designed to send requests to a Ripple Server (over websockets) and retrieve the response.
  *
  */
trait FlowSegments extends StrictLogging {

  // TODO: Revisit how to handle implicits via => on trait or per method.
  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  val jsonQueueSink: Sink[Json, SinkQueueWithCancel[Json]] = Sink.queue[Json]

  /** Creates the flow to use (note: not re-usable!) within the overall flow using
    *  This is a webSocketClientFlow because its actually easier than singleRequest
    *  Normally you would configure this context better ;-/ now its a simple flow.
    *
    *  @param url
    *
    *  @return
    */
  protected def createConfiguredSocket(url: String): Flow[Message, Message, Future[WebSocketUpgradeResponse]] = {
    logger.debug(s"Creating COnfigured Socket at URL: $url")
    Http().webSocketClientFlow(WebSocketRequest(url))
  }

  /** Quick hack to control throttling - this is not production grade I think. */
  protected def createThrottlingFlow(tps: Int = 10, burstFactor: Float = 2.0f): Flow[Message, Message, NotUsed] = {
    Flow[Message].throttle(
      elements = tps,
      per = FiniteDuration(1L, TimeUnit.SECONDS),
      maximumBurst = (tps * burstFactor).toInt,
      mode = ThrottleMode.Shaping
    )

  }

  /** Extracts a Akka-Flow message to text, both Strict and Async messages handles
    *  If it fails or is a BinaryMessage then Error is returned.
    */
  val messageToString: Flow[Message, ErrorOr[String], NotUsed] = {
    Flow[Message].mapAsync(2)(extractText)
  }

  val stringToJson: Flow[ErrorOr[String], ErrorOr[Json], NotUsed] = {
    Flow[ErrorOr[String]].map(_.flatMap(CirceUtils.parseAsJson))
  }

  val jsonToString: Flow[ErrorOr[Json], ErrorOr[String], NotUsed] = {
    Flow[ErrorOr[Json]].map(_.map(CirceUtils.print))
  }

  /** Converts Circe JSON into a text message suitable for Akka - HTTP */
  val jsonToMessage: Flow[Json, TextMessage.Strict, NotUsed] = {
    Flow[Json].map(json ⇒ TextMessage(CirceUtils.print(json)))
  }

  val messageToJson: Flow[Message, ErrorOr[Json], NotUsed] = {
    Flow[Message].via(messageToString).via(stringToJson)
  }

  /** Code reminder of how to send a single message and not close the strweam immediately after sending! */
  def makeSingleSource[T](rq: T): Source[T, Promise[Option[T]]] = {
    Source(List(rq)).concatMat(Source.maybe[T])(Keep.right)
  }

  protected def extractText(m: Message): Future[ErrorOr[String]] = {
    m match {
      case message: TextMessage.Strict   ⇒ Future.successful(message.text.asRight)
      case message: TextMessage.Streamed ⇒ message.textStream.runFold("")(_ + _).map(_.asRight)(system.dispatcher)
      case bin: BinaryMessage            ⇒ Future.successful(OError("Binary Response not handled in WebSocket Msgs").asLeft)
    }
  }

}
