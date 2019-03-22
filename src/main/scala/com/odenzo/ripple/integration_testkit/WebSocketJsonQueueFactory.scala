package com.odenzo.ripple.integration_testkit

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.control.NonFatal

import akka.Done
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.ws.{InvalidUpgradeResponse, ValidUpgrade, WebSocketUpgradeResponse}
import akka.stream.{ActorMaterializer, Attributes, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, SinkQueueWithCancel, Source, SourceQueueWithComplete}
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

import com.odenzo.ripple.models.support.RippleWsNode
import com.odenzo.ripple.utils.caterrors.CatsTransformers.{ErrorOr, ErrorOrFT}
import com.odenzo.ripple.utils.caterrors.{AppError, AppException, AppJsonError, ErrorOrFT, OError}

/** Long running WebSocket now pruned to just Json => ErrorOr[Json]
  *  This does a synchronous send and recieve, but due to the wonders of Akka Stream Queue still not MT safe.
  *  Thus we put an Actor in front of it.
  *  Has a Source Queue and Sink Queue.
  */
class WebSocketJsonQueueFactory(node: RippleWsNode, logMessages: Boolean = true, throttleTps: Option[Int] = Some(4))
    extends StrictLogging
    with FlowSegments {

  implicit val system: ActorSystem             = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor    = system.dispatcher

  // Statics Basically
  private val sourceQueue: Source[Json, SourceQueueWithComplete[Json]] =
    Source.queue[Json](bufferSize = 20, overflowStrategy = OverflowStrategy.backpressure)
  private val sinkQueue = Sink.queue[ErrorOr[Json]]()

  //  type flowSig = RunnableGraph[
  //    ((SourceQueueWithComplete[Json], Future[WebSocketUpgradeResponse]), SinkQueueWithCancel[ErrorOr[Json]])
  //  ]

  def connectAsync(): ErrorOrFT[WebSocketJsonConnection] = {
    val ((srcQ, upgrade), sinkQ) = buildFlow().run()

    val connected: Future[ErrorOr[String]] = upgrade.map {
      case ValidUpgrade(_, subProtocol) ⇒
        logger.debug(s"WebSocket Upgraded to Protocal: $subProtocol")
        subProtocol.getOrElse("No Sub Protocol").asRight

      case InvalidUpgradeResponse(rs: HttpResponse, cause: String) ⇒
        val err = new OError(s"Upgrade of WebSocket Failed: $cause")
        logger.error(err.show)
        err.asLeft

    }
    val instance =  ErrorOrFT(connected).map(_ ⇒ new WebSocketJsonConnection(srcQ, sinkQ, node))

    instance.leftMap { e ⇒
      logger.error(s"Caution -- WebSocket Connection to $node Failed", e)
      e
    }

    instance
  }
  def connect(connectTimeout: Duration = Duration("4 seconds")): ErrorOr[WebSocketJsonConnection] = {
    val async: ErrorOrFT[WebSocketJsonConnection] = connectAsync()
    val sync: ErrorOr[WebSocketJsonConnection] = ErrorOrFT.sync(async, connectTimeout)
    sync
  }

  protected def buildFlow(): RunnableGraph[
    ((SourceQueueWithComplete[Json], Future[WebSocketUpgradeResponse]), SinkQueueWithCancel[ErrorOr[Json]])
  ] = {

    val url: String = node.url
    logger.trace(s"Building RippleWebSocket to $url")

    val configedQueue = sourceQueue
      .withAttributes(Attributes.logLevels(onElement = Logging.WarningLevel))
      .log("Queue Rq:", _.spaces2)

    val source = throttleTps match {
      case None      ⇒ configedQueue.via(jsonToMessage)
      case Some(tps) ⇒ configedQueue.via(jsonToMessage).via(createThrottlingFlow(tps))
    }

    val fullStream: RunnableGraph[
      ((SourceQueueWithComplete[Json], Future[WebSocketUpgradeResponse]), SinkQueueWithCancel[ErrorOr[Json]])
    ] = source
      .viaMat(createConfiguredSocket(url))(Keep.both)
      .via(messageToString)
      .via(stringToJson) // If this fails then the raw string is stored in the error
      .recover {
        case NonFatal(e) ⇒ AppException("Exception in Stream Processing", e).asLeft
      }
      .toMat(sinkQueue)(Keep.both)

    fullStream
  }

}

/** This is a little more generic instance, although typically both A and B as Json.
  *  @param src
  *  @param sink
  *  @tparam A
  *  @tparam B
  */
class WebSocketJsonConnection(
                               src: SourceQueueWithComplete[Json],
                               sink: SinkQueueWithCancel[ErrorOr[Json]],
                               node: RippleWsNode
) extends StrictLogging {

  override def toString: String = s"WebSocketQueue $node ${src.toString}"

  /** This is not multithread safe, and is intended for use from a single threaded actor (syncing on response).
    *  This is a synchronous call from the perspective of the caller.
    *  This is sometimes handy with Future chains though.
    *
    *
    */
  def offer(rq: Json)(implicit ec: ExecutionContext): ErrorOrFT[String] = {
    logger.trace("You Sent an Raw Send - No Syncing Not MT Safe")
    // This is a future just to put on the outbound queue to send.
    val offer: ErrorOrFT[String] = pushRequest(rq)
    offer

  }

  /** Experiment to block on offering but NOT on the pull from sink */
  def offerSync(rq: Json)(implicit ec: ExecutionContext): ErrorOrFT[String] = {
    logger.trace("You Sent an Sync on Offer - Not MT Safe and kind of Worthless.")
    val offerTimeout: Duration = Duration("4 seconds")

    val offer: Future[Either[AppError, String]] = pushRequest(rq).value
    val ok: Either[AppError, String]            = Await.result(offer, offerTimeout)
    ErrorOrFT.fromEither(ok)

  }

  def take()(implicit ec: ExecutionContext): ErrorOrFT[Json] = pullResult()

  def takeSync()(implicit ex: ExecutionContext): ErrorOr[Json] = {
    val fv: ErrorOrFT[Json] = take()
    ErrorOrFT.sync(fv, Duration("4 seconds"))
  }

  /** Synchronous send completes the future on Sink. <em>Not Multithread Safe</em><em>IMPURE</em>  */
  def ask(rq: Json)(implicit ec: ExecutionContext): ErrorOr[RequestResponse[Json, Json]] = {
    ErrorOrFT.sync(send(rq), Duration("10 seconds"))
  }

  def send(rq: Json)(implicit ec: ExecutionContext): ErrorOrFT[RequestResponse[Json, Json]] = {
    logger.debug(s"ASync Send To Node: $node")

    // This is a future just to put on the outbound queue to send.
    val offer: ErrorOrFT[String]                        = pushRequest(rq)
    val ans: ErrorOrFT[Json]                            = offer.flatMap(x ⇒ pullResult())
    val wrapped: ErrorOrFT[RequestResponse[Json, Json]] = ans.map(rs ⇒ RequestResponse(rq, rs))
    wrapped
  }

  /** FIXME: Define the semantics of shutdown and implement. Will probably take a file so a Future */
  def shutdown(): Future[Done] = {
    src.complete()
    sink.cancel() // Exact semantics I forgot!
    val futDone: Future[Done] = src.watchCompletion()
    futDone
  }

  /** This is exposed because its testing, but it IS NOT multithread safe.
    *  Typically use the send function to send a request and get the response.
    *  @param rq   Request send queue for conversion and sending out over websocket.
    *  @param ec Context used to add to the queue, which may block (very short time blocking normally)
    *
    *  @return
    */
  def pushRequest(rq: Json)(implicit ec: ExecutionContext): ErrorOrFT[String] = {
    /*
      The contract for offer returns a Future that may fail, if the previous future hasn't completed.
      Hopefully this will change in future, but for now we make single threaded here.
      Note that we are assuming that the queries over the websocket to Ripple are FIFO.
      This isn't actually documentented anyway. Otherwise we have to make sequential
      or put in a id correlation stage.
       Pull is called inside the lock to get in line, but not
       fulfilled in lock (its a future). So we are getting in what I assume is ordered pull queue.
       and can have a few in-flight messages. This *ALSO* assumes the websocket if FIFO in rippled server. Sigh!
     */

    val res: Future[Either[AppError, String]] = src
                                                          .offer(rq)
                                                          .map {
        case QueueOfferResult.Enqueued    ⇒ "QueueOfferResult.Enqueued".asRight
        case QueueOfferResult.Dropped     ⇒ OError("QueueOfferResult.Dropped").asLeft
        case QueueOfferResult.QueueClosed ⇒ OError("Source Queue closed").asLeft
        case QueueOfferResult.Failure(ex) ⇒ AppException(s"Offer failed", ex).asLeft
        case other                        ⇒ OError("Other Error " + other).asLeft
      }
                                                          .recover {
        case e: IllegalStateException ⇒ new AppException("Recover:Offer before previous future completed", e).asLeft
        case NonFatal(e)              ⇒ new AppException("Recovered: Not -Unknown Error + to Queue", e).asLeft
      }

    ErrorOrFT(res).leftMap(ex ⇒ new AppJsonError("Error Pushing Request", rq, Some(ex)))

  }

  /** This is normally private use, but leave it open while elaborate test cases */
  protected def pullResult()(implicit ec: ExecutionContext): ErrorOrFT[Json] = {
    // FIXME: Need a special OError for Stream Completed
    val rs: Future[ErrorOr[Json]] = sink.pull().map {
      case None        ⇒ OError("None on return queue means stream completed.").asErrorOr
      case Some(errOr) ⇒ errOr
    }
    ErrorOrFT(rs)
  }
}
