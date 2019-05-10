package com.odenzo.ripple.integration_tests.integration_testkit

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContextExecutor

import cats._
import cats.data._
import cats.implicits._
import akka.util.Timeout
import com.typesafe.scalalogging.{Logger, StrictLogging}
import org.scalatest.{BeforeAndAfterAll, EitherValues, FunSuiteLike, Matchers}
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.time.{Seconds, Span}

import com.odenzo.ripple.models.atoms.{AccountAddr, AccountKeys, Drops, LedgerIndex, Memos}
import com.odenzo.ripple.models.support.{RippleAccountRW, RippleWsNode}
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.CommonTx
import com.odenzo.ripple.utils.caterrors.AppError
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr

/** Test fixture to mixin to scalatests for integration testing.
  *  Bit of a hack as they reference the MyTestServers object which instanciates Akka systems.
  */
trait IntegrationTestFixture
    extends StrictLogging
    with Matchers
    with EitherValues
    with PatienceConfiguration
    with ScalaFutures
    with FunSuiteLike
    with BeforeAndAfterAll {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(15, Seconds), interval = Span(0.1, Seconds))

  /** Global Execution Context as Helper -- NOT implicit */
  val ecGlobal: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  val GENESIS: RippleAccountRW = RippleAccountRW.GENESIS

  //val GENESIS_KEYS = AccountKeys(GENESIS.address, "sepc256k1",GENESIS.secret,RippleSeed(""),GENESIS.pu)
  implicit val timeout: Timeout = akka.util.Timeout(3, TimeUnit.SECONDS)

  val prodNet: RippleWsNode = RippleWsNode.ripplenet

  val testNet: RippleWsNode = RippleWsNode("TestNet", "wss://s.altnet.rippletest.net:51233", isAdmin = false)

  val testNode: RippleWsNode = RippleWsNode("TestNode", "ws://127.0.0.1:60066/", isAdmin = true)

  val node            = RippleWsNode(url = "ws:127.0.0.1", isAdmin = true, name = "LocalPlaceHolder")
  val defaultTxParams = CommonTx(fee = Some(Drops(100)))

  def fullOptions = CommonTx(Memos.fromText("Default Memo"), None, Some(LedgerIndex.MAX), fee = Some(Drops(666)))

  // We also need to get account txn sequence for some tests.
  def nextTxnSequence(account: AccountAddr): Unit = {}

  import org.scalatest.Tag


  def getOrLog[T](ee: ErrorOr[T], msg: String = "Error: ", loggger: Logger = logger): T = {
    ee.leftMap { e ⇒
      logger.error(s"$msg: " + AppError.summary(e))
      assert(false, s"Auto Test of $msg")

    }
    ee.right.value
  }


  object IntegrationTest extends Tag("com.odenzo.ripple.network.IntegrationTest")


}
