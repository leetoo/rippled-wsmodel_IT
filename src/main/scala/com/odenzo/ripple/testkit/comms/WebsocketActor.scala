package com.odenzo.ripple.testkit.comms

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Success

import akka.actor.{ActorSystem, TypedActor, TypedProps}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Json, JsonObject}
import io.circe.syntax._

/** Typed Websocket actor that sends JSON and receives JSON
  * Underneat it still has to use the Akka Streams stuff
  **/
trait WebSocketTypedActor {

  //   def send(rq:JsonObject): Unit //fire-forget

    def sendAsync(rq:JsonObject): Future[Json] //non-blocking send-request-reply
//
//    def sendSync(rq:JsonObject): Json //blocking send-request-reply
//
//    def squareNow(i: Int): Int //blocking send-request-reply
//
//    @throws(classOf[Exception]) //declare it or you will get an UndeclaredThrowableException
//    def squareTry(i: Int): Int //blocking send-request-reply with possible exception

}

class WebSocketTypedActorImpl()  extends WebSocketTypedActor with StrictLogging {


  // Sink is input, source is output :-)
//  val sink =
//  Flow.fromSinkAndSourceCoupled(sink,source)
  // Flow.fromSinkAndSource, or Flow.map for a request-response protocol

  def preStart(): Unit = {
    logger.info("PreStart Called")
  }

  def preReStart(): Unit = {
    logger.info("PreReStart Called")
  }

  override def sendAsync(rq: JsonObject): Future[Json] = Future.successful(rq.asJson)
}

object WebsocketActor {

  implicit val system      : ActorSystem              = ActorSystem("Typed")
  implicit val materializer: ActorMaterializer        = ActorMaterializer()
  implicit val ec          : ExecutionContextExecutor = system.dispatcher

  val wsa: WebSocketTypedActor =
    TypedActor(system).typedActorOf(TypedProps(
                                                classOf[WebSocketTypedActor],
                                                new WebSocketTypedActorImpl()
                                                ), "name"
                                    )

  TypedActor(system).stop(wsa)
}
