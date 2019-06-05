package com.odenzo.ripple.fixturesgen

import java.security.SecureRandom
import java.util.UUID

import io.circe.Decoder
import io.circe.syntax._
import org.scalatest.FunSuite

import com.odenzo.ripple.integration_testkit.JsonReqRes
import com.odenzo.ripple.localops.utils.CirceUtils
import com.odenzo.ripple.localops.utils.caterrors.AppError
import com.odenzo.ripple.models.atoms.{AccountAddr, AccountKeys, Drops}
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRq, SignRs, SubmitRq, SubmitRs}

/** This is to make some JSON data with transactions and the submit and sign stuff.
  * Goal is to use to test transaction hash which is input to signing and also signing.
  * Does this for secp and ed25519 writing results to special log files (see code)
  *
  * */
class TxnFixtureMakerTest extends FunSuite with FixtureGeneratorUtils {

  import cats.implicits._

  val secureRND: SecureRandom = SecureRandom.getInstanceStrong

  def makePassword(): String = {
    secureRND.generateSeed(4) // 32 bit int
    UUID.randomUUID().toString
  }

  test("secp256k1 fixture generator") {
    // Create some new accounts, activate the first with Genesis.
    // Activate the remainder with the first account.
    // Do one more txn to topup the first of the  accounts.
    // We DO NOT user the create account helper because want the raw jsons.
    val keyType = "secp256k1"
    val results = generateFixture(keyType, 10)
    getOrLog(results)

  }

  test("ed25519 fixture generator"){
    // Create some new accounts, activate the first with Genesis.
    // Activate the remainder with the first account.
    // Do one more txn to topup the first of the  accounts.
    // We DO NOT user the create account helper because want the raw jsons.
    val keyType = "ed25519"
    val results = generateFixture(keyType, 10)
    getOrLog(results)

  }

  def generateFixture(keyType: String, numWallets: Int): Either[AppError, List[JsonReqRes]] = {
    val results = for {
      wallets  <- ScenerioBuilder.makeWallets(numWallets, keyType)
      accounts = wallets.map(_._1)
      walletRR = wallets.map(_._2)
      sender   = accounts.head
      dests    = accounts.tail.map(_.address)
      _        = logAnswerToFile(s"logs/${keyType}_wallets.json", walletRR)
      _        = logger.info(s"Created ${wallets.length} wallets with $keyType")

      // Fund the sending account only, we don't care about these results.
      funder     = ScenerioBuilder.genesis
      genSeq     ← ScenerioBuilder.getAccountSequence(funder.address)
      fundSender = ScenerioBuilder.createXrpTransfer(funder, sender.address, Drops.fromXrp(10000), genSeq)
      funded     ← ScenerioBuilder.serverSignAndSubmit(fundSender.asJsonObject, funder)
      _          = ScenerioBuilder.advanceLedger()
      txn        ← sendToAll(sender, dests, Drops.fromXrp(555))

      _ = logAnswerToFile(s"logs/${keyType}_txn.json", txn)
    } yield txn
    results
  }

  def sendToAll(sender: AccountKeys, addresses: List[AccountAddr], amt: Drops): Either[AppError, List[JsonReqRes]] = {
    logger.info(s"Sending from ${sender.address} to ${addresses.length} other accounts.")
    addresses.traverse { dest ⇒
      serverSignTxn(sender, dest, amt)
    }
  }

  /** Signs and Submits an XRP Transfer on Server.
    *   This should work for both secp and ed
    * @param sender Sender of XRP
    * @param recv   Receiver of XRP
    * @param amt    Amount to send
    * @return The raw JSON request response pair from the *signing*
    */
  def serverSignTxn(sender: AccountKeys, recv: AccountAddr, amt: Drops): Either[AppError, JsonReqRes] = {
    for {
      seq       <- ScenerioBuilder.getAccountSequence(sender.address)
      txn       = ScenerioBuilder.createXrpTransfer(sender, recv, amt, seq)
      txjson    = CirceUtils.pruneNullFields(txn.asJsonObject)
      _         = logger.debug(s"About to Sign ${sender.secret} \nTxn: ${txjson.asJson.spaces4}")
      signRq    = SignRq(txjson.asJson, sender.master_seed, offline = false, key_type = sender.key_type.v)
      signJson  = CirceUtils.pruneNullFields(signRq.asJsonObject)
      _         = logger.info(s"Internal Sign: ${signJson.asJson.spaces4}")
      signing   <- doCmdCallKeepJson(signJson, Decoder[SignRs])
      signRs    = signing._1
      submitRq  = SubmitRq(signRs.tx_blob).asJsonObject
      submitted ← doTxnCallKeepJson(submitRq.asJsonObject, Decoder[SubmitRs])
      _         = ScenerioBuilder.advanceLedger()
    } yield signing._2
  }



}
