package com.odenzo.ripple.integration_testkit

import scala.collection.immutable
import scala.concurrent.ExecutionContextExecutor

import io.circe.syntax._
import io.circe.{Json, JsonObject}
import org.scalatest.FunSuite
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}

import com.odenzo.ripple.models.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.models.wireprotocol.serverinfo.ServerInfoRq
import com.odenzo.ripple.models.wireprotocol.subscriptions.SubscribeLedgerRq
import com.odenzo.ripple.testkit.comms.{WebSocketJsonConnection, WebSocketJsonQueueFactory}
import com.odenzo.ripple.testkit.helpers.JsonReqRes

class WebSocketQ$ITest extends FunSuite with IntegrationTestFixture with PatienceConfiguration {

  //implicit val serialization   = native.Serialization
  //implicit val formats         = DefaultFormats

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(15, Seconds), interval = Span(0.1, Seconds))

  import cats.implicits._

  val ws = new WebSocketJsonQueueFactory(testNet, logMessages = true, throttleTps = None)

  implicit val ec: ExecutionContextExecutor = ecGlobal

  test("Say Hello") {
    logger.info("Hello@")
  }

  test("Send One") {
    val connection: WebSocketJsonConnection = ws.connect().right.value
    val req                                 = ServerInfoRq().asJsonObject
    val result                              = connection.ask(req)
    logger.info(s"Raw Result $result")
    logWithRequest(req, result)
    result.isRight shouldBe true

  }

  /** These are test for when Queues work correctly in Akka streams based on my world view!
    *  test("Async  - Fails") {
    *  val connection: WebSocketQInstance[Json, Json] = ws.connect()
    *  val reqs: immutable.Seq[Json] = (1 to 2).map(_ ⇒ ServerInfoRq().asJson)
    *  val ans: immutable.Seq[ErrorOrFT[String]] = reqs.map(rq ⇒ connection.offer(rq))
    *  val rs: immutable.Seq[ErrorOrFT[Json]] = ans.map(_ ⇒ connection.take())
    *  val synced: immutable.Seq[Either[caterrors.Error, Json]] = rs.map(_.value.futureValue)
    *  synced.foreach(logResult)
    *  ans.foreach(_.isRight shouldBe true)
    *  }
    *
    *  test("Sync on Offer - Fails") {
    *  val connection: WebSocketQInstance[Json, Json] = ws.connect()
    *  val reqs: immutable.Seq[Json] = (1 to 2).map(_ ⇒ ServerInfoRq().asJson)
    *  val ans: immutable.Seq[ErrorOrFT[String]] = reqs.map(rq ⇒ connection.offerSync(rq))
    *  val rs: immutable.Seq[ErrorOrFT[Json]] = ans.map(_ ⇒ connection.take())
    *  val synced: immutable.Seq[Either[caterrors.Error, Json]] = rs.map(_.value.futureValue)
    *  synced.foreach(logResult)
    *  ans.foreach(_.isRight shouldBe true)
    *  }
    */
  test("Send Sync MT Safe", IntegrationTest) {
    val connection: WebSocketJsonConnection     = ws.connect().right.value
    val reqs: immutable.Seq[JsonObject] = (1 to 2).map(_ ⇒ ServerInfoRq().asJsonObject)
    val ans: immutable.Seq[ErrorOr[JsonReqRes]] = reqs.map(rq ⇒ connection.ask(rq))

    ans.foreach(logResult)
    ans.foreach(_.isRight shouldBe true)
    connection.shutdown()
  }

  test("Example of Using Plain Queue for Subscribe Flow", IntegrationTest) {
    val connection: WebSocketJsonConnection = ws.connect().right.value
    val rq: JsonObject = SubscribeLedgerRq().asJsonObject
    val offer: ErrorOr[String]              = connection.offerSync(rq).value.futureValue
    offer.isRight shouldBe true

    for (_ ← 1 to 10) {
      val ans: ErrorOr[Json] = connection.takeSync()
      logResult(ans)
      ans.isRight shouldBe true
    }
    connection.shutdown()
  }

  //  test("Sending a Bunch == Async") {
  //    // Note this is using the common socket.
  //    val count = 500
  //    logger.info(s"Sending $count requests")
  //    val req = (1 to count).toList.map(_ ⇒ ServerInfoRq().asJson)
  //    val done: ErrorOr[List[RippleCallData]] = req.traverse(_ ⇒ ws.send(ServerInfoRq().asJson))
  //    val res = done.right.value
  //
  //    logger.info(s"Completed with Count ${res.size}")
  //    res.foreach(r ⇒ logger.debug(s"Msg: ${r.show} \n\n"))
  //  }

  def logWithRequest[A](rq: JsonObject, rs: ErrorOr[A]): Unit = {
    rs match {
      case Left(err) ⇒
        logger.error(s"Sending:\n${rq.asJson.spaces2}\n RESULTED in error\n${err.show}")

      case Right(ok) ⇒ logger.debug(ok.toString)
    }
  }

  def logResult[A](rs: ErrorOr[A]): Unit = {
    rs match {
      case Left(err) ⇒
        logger.error(s"Sending:\n RESULTED in error\n${err.show}")

      case Right(ok) ⇒ logger.debug(ok.toString)
    }
  }
}
