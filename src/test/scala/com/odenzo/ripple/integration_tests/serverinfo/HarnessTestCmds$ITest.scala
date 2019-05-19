package com.odenzo.ripple.integration_tests.serverinfo

import java.nio.file.{Path, Paths}
import scala.concurrent.ExecutionContextExecutor

import cats._
import cats.data._
import cats.implicits._

import com.odenzo.ripple.integration_testkit.{OtherTestCommandHarness, TestCallResults, TestCommandHarness, WebSocketJsonConnection, WebSocketJsonQueueFactory}
import com.odenzo.ripple.integration_tests.integration_testkit._
import com.odenzo.ripple.models.atoms.AccountAddr
import com.odenzo.ripple.models.support.{Commands, RippleAccountRO, RippleRq, RippleWsNode}
import com.odenzo.ripple.models.wireprotocol.accountinfo.{AccountCurrenciesRq, AccountCurrenciesRs}
import com.odenzo.ripple.models.wireprotocol.serverinfo.{FeeRq, FeeRs}
import com.odenzo.ripple.localops.utils.caterrors
import com.odenzo.ripple.localops.utils.caterrors.CatsTransformers.ErrorOr

class HarnessTestCmds$ITest extends IntegrationTestFixture {

  val accAddr: AccountAddr = AccountAddr("rLqxc4bxqRVVt63kHVk22Npmq9cqHVdyR")
  val swfRO = RippleAccountRO("Steve Main", accAddr)

  /*
  These are motly simple commands that have no special pre-requisites. Just normal or admin access.
   */
  val testNetConn: WebSocketJsonConnection = new WebSocketJsonQueueFactory(testNet).connect().right.value





  implicit val ec: ExecutionContextExecutor = ecGlobal
//
//
//  test("FeeCmd") {
//    val answer: TestCallResults[FeeRq, FeeRs] = fee.send(FeeRq())
//    logger.info("FeeRq:" + answer.showMe)
//    answer.hasErrors shouldBe false
//    answer.dumpSerializedForUnitTests(Paths.get("/tmp/traces"), 1)
//    answer.dumpSerializedForUnitTests(Paths.get("/tmp/traces"), 2)
//
//  }

//  test("AccountBalance") {
//    val liveNetConn: WebSocketJsonConnection = new WebSocketJsonQueueFactory(prodNet).connect().right.value
//    val prod: TestCallResults[AccountCurrenciesRq, AccountCurrenciesRs] =
//      TestCommandHarness.doCommand(Commands.accountInfoCmd.AccountCurrenciesCmd, liveNetConn, AccountCurrenciesRq
//                                                                                            (accAddr))
//    logger.info("Results: " + prod.showMe())
//    liveNetConn.shutdown()
//    prod.hasErrors shouldBe false
//
//  }
//
//  test("Semantic Inquire Error") {
//    // logger.info("AccountCurrencies: " + RippleTestCall.dump(prod))
//
//    val testNet =
//      TestCommandHarness.doCommand(Commands.AccountInfo.AccountCurrenciesCmd, testNetConn, AccountCurrenciesRq
//                                                                                                 (accAddr))
//    logger.info("TestNet Errors" + testNet.showMe())
//    testNet.hasErrors shouldBe true
//
//  }
//
//  test("Bad Connection") {
//    // Send to Local Host that is Not there
//    val badServer = RippleWsNode("Bad", "wss://localhost:666", isAdmin = false)
//    val badConn: ErrorOr[WebSocketJsonConnection] = new WebSocketJsonQueueFactory(badServer).connect()
//    val err: caterrors.AppError = badConn.left.value
//    logger.error(s"Error Class ${err.getClass}")
//    logger.error(s"Bad Connection Timeout Error:  ${err.show}")
//  }
//
//  test("No Reply Over A Connection") {
//
//  }
//
//  test("Reply With Non-Parseable Json") {
//
//  }
//
//  test("Reply with JSON but no GenericRippleResponse Parseable") {
//    // Not sure the best way to test this...
//    val echoServer = RippleWsNode("Echo WebSocket", "wss://echo.websocket.org/", isAdmin = false)
//    val badConn: WebSocketJsonConnection = new WebSocketJsonQueueFactory(echoServer).connect().right.value
//    val bad =
//      TestCommandHarness.doCommand(Commands.AccountInfo.AccountCurrenciesCmd, badConn, AccountCurrenciesRq(accAddr))
//
//    logger.info("BAD SHOWME START ==========\n" + bad.showMe() + "\n ======================= END BAD SHOWME")
//    badConn.shutdown()
//    bad.generic.isLeft shouldBe true
//
//  }

}
