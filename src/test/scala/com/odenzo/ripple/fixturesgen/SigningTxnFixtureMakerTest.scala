package com.odenzo.ripple.fixturesgen

import java.security.SecureRandom
import java.util.UUID

import io.circe.Decoder
import io.circe._
import io.circe.syntax._
import org.scalatest.FunSuite

import com.odenzo.ripple.integration_testkit.IntegrationTestFixture
import com.odenzo.ripple.models.atoms.{AccountAddr, AccountKeys, Currency, Drops, FiatAmount, Memos, Script}
import com.odenzo.ripple.models.utils.CirceUtils
import com.odenzo.ripple.models.utils.caterrors.AppError
import com.odenzo.ripple.models.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.{CommonTx, RippleTransaction, TrustSetTx}
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRq, SignRs, SubmitRq, SubmitRs}
import com.odenzo.ripple.testkit.helpers.{JsonReqRes, LogHelpers, ServerCallUtils, ServerOps, TraceRes, TxnFactories}

/**
  * First fixture is to create some accounts and send a series of transactions that are saved to disk.
  * The txn send from both ed25519 and secp256k1 accounts.
  * More txn types to be added incrementally.
  **/
class SigningTxnFixtureMakerTest extends FunSuite with IntegrationTestFixture {

  import cats.implicits._


  test("secp256k1 fixture generator") {
    // Create some new accounts, activate the first with Genesis.
    // Activate the remainder with the first account.
    // Do one more txn to topup the first of the  accounts.
    // We DO NOT user the create account helper because want the raw jsons.
    val keyType                                      = "secp256k1"
    val keys: List[AccountKeys] = getOrLog(generateFixture(keyType, 10))
    

    val more: List[(TrustSetTx, AccountKeys)]                     = generateAdditionalTxn(keys)
    val moreResults: List[(TraceRes[SignRs], TraceRes[SubmitRs])] = getOrLog(executeOnServer(more))
    val signRqRs: List[JsonReqRes]                                = moreResults.map(v ⇒ v._1.rr)
    LogHelpers.logAnswerToFile(s"logs/${keyType}_more_txn.json", signRqRs)
    // Log to disk

  }

  test("ed25519 fixture generator") {
    // Create some new accounts, activate the first with Genesis.
    // Activate the remainder with the first account.
    // Do one more txn to topup the first of the  accounts.
    // We DO NOT user the create account helper because want the raw jsons.
    val keyType = "ed25519"
    val keys = getOrLog(generateFixture(keyType, 10)) // This logs the inital transaction to files

    val more: List[(TrustSetTx, AccountKeys)]                     = generateAdditionalTxn(keys)
    val moreResults: List[(TraceRes[SignRs], TraceRes[SubmitRs])] = getOrLog(executeOnServer(more))
    val signRqRs: List[JsonReqRes]                                = moreResults.map(v ⇒ v._1.rr)
    LogHelpers.logAnswerToFile(s"logs/${keyType}_more_txn.json", signRqRs)
  }

  /**
    *
    * @param fundedAccounts Master AccountKeys (with correct address) of activated accounts.
    */
  def generateAdditionalTxn(fundedAccounts: List[AccountKeys]): List[(TrustSetTx, AccountKeys)] = {

    // What to do... maybe a TrustSet from tails to head and then transfor some IOU
    val amount: BigDecimal  = BigDecimal("555.666")
    val currency: Currency  = Currency("NZD")
    val issuer: AccountAddr = fundedAccounts.head.address
    val script              = Script(currency, issuer)

    val trustAmount: FiatAmount = FiatAmount(amount, script)
    // Make all the accounts
    fundedAccounts.tail.map(acctKeys ⇒ genTrustLine(issuer, acctKeys, trustAmount))
  }

  /**
    *
    * @param issuer
    * @param account
    * @param amount
    * @return List of RippleTransaction to sign and submit using the associated AccountKeys
    */
  def genTrustLine(issuer: AccountAddr, account: AccountKeys, amount: FiatAmount): (TrustSetTx, AccountKeys) = {
    val commonTx = CommonTx(
      fee = Some(Drops.fromXrp("100")),
      signingPubKey = Option(account.signingPubKey),
      memos = Memos.fromText("Test Transaction")
    )
    // Need to add the account setting the trust line with issuer
    (TrustSetTx(account.address, amount, base = commonTx), account)
  }

  /**
    * Crestes N wallets with specified key type. Funding the first one with XRP from Genesis
    * Then funds the remaining from the first account.
    * We use low level things here to save the Json messages to disk.
    *
    * @return list of AccountKeys for created accounts.
    **/
  def generateFixture(keyType: String, numWallets: Int): Either[AppError, List[AccountKeys]] = {
    val results: Either[AppError, List[AccountKeys]] = for {
      wallets  <- TxnFactories.makeWallets(numWallets, keyType)
      accounts = wallets.map(_._1)
      walletRR = wallets.map(_._2)
      sender   = accounts.head
      dests    = accounts.tail.map(_.address)
      _        = LogHelpers.logAnswerToFile(s"logs/${keyType}_wallets.json", walletRR)
      _        = logger.info(s"Created ${wallets.length} wallets with $keyType")

      // Fund the sending account only, we don't care about these results.
      funder     = ServerOps.genesis
      genSeq     ← ServerOps.getAccountSequence(funder.address)
      fundSender = TxnFactories.createXrpTransfer(funder, sender.address, Drops.fromXrp(10000), genSeq)
      funded     ← ServerOps.serverSignAndSubmit(fundSender.asJsonObject, funder)
      _          = ServerOps.advanceLedger()
      txn        ← sendToAll(sender, dests, Drops.fromXrp(555))
      _          = LogHelpers.logAnswerToFile(s"logs/${keyType}_txn.json", txn)

    } yield accounts
    results
  }

  def sendToAll(sender: AccountKeys, addresses: List[AccountAddr], amt: Drops): Either[AppError, List[JsonReqRes]] = {
    logger.info(s"Sending from ${sender.address} to ${addresses.length} other accounts.")
    addresses.traverse { dest ⇒
      serverSignTxn(sender, dest, amt)
    }
  }

  /** Signs and Submits an XRP Transfer on Server.
    * This should work for both secp and ed
    *
    * @param sender Sender of XRP
    * @param recv   Receiver of XRP
    * @param amt    Amount to send
    *
    * @return The raw JSON request response pair from the *signing*
    */
  def serverSignTxn(sender: AccountKeys, recv: AccountAddr, amt: Drops): Either[AppError, JsonReqRes] = {
    for {
      seq       <- ServerOps.getAccountSequence(sender.address)
      txn       = TxnFactories.createXrpTransfer(sender, recv, amt, seq)
      signRq    = SignRq(txn.asJson, sender.master_seed, offline = false, key_type = sender.key_type.v)
      signJson  = CirceUtils.pruneNullFields(signRq.asJsonObject)
      _         = logger.info(s"Internal Sign: ${signJson.asJson.spaces4}")
      signing   <- ServerCallUtils.doCmdCallKeepJson(signJson, Decoder[SignRs])
      signRs    = signing._1
      submitRq  = SubmitRq(signRs.tx_blob).asJsonObject
      submitted ← ServerCallUtils.doTxnCallKeepJson(submitRq.asJsonObject, Decoder[SubmitRs])
      _         = ServerOps.advanceLedger()
    } yield signing._2
  }

}
