package com.odenzo.ripple.fixturesgen

import java.time.Instant

import cats.data.Nested
import io.circe.{Decoder, JsonObject}
import io.circe.syntax._
import org.scalatest.FunSuite

import com.odenzo.ripple.integration_testkit.IntegrationTestFixture
import com.odenzo.ripple.models.atoms.RippleTxnType.OfferCreate
import com.odenzo.ripple.models.atoms.{AccountAddr, AccountKeys, Currency, Drops, FiatAmount, RippleTime, Script}
import com.odenzo.ripple.models.utils.CirceUtils
import com.odenzo.ripple.models.utils.caterrors.AppError
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.{CommonTx, OfferCreateTx, TrustSetTx}
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRq, SignRs, SubmitRq}
import com.odenzo.ripple.testkit.helpers.TxnFactories.RippleTxnCommand
import com.odenzo.ripple.testkit.helpers.{FullKeyPair, JsonReqRes, LogHelpers, ServerCallUtils, ServerOps, TraceHelpers, TracedRes, TxnFactories}

/**
  * First fixture is to create some accounts and send a series of transactions that are saved to disk.
  * The txn send from both ed25519 and secp256k1 accounts.
  * More txn types to be added incrementally.
  **/
class SigningTxnFixtureMakerTest extends FunSuite with IntegrationTestFixture {

  import cats.implicits._

  test("Dump Scenario Accounts") {
    val accounts: List[FullKeyPair] = getOrLog(StandardScenarios.allScenarioAccounts)
    val listOfAccounts: List[JsonObject] = accounts.map{ fkp ⇒
        val masterJson = fkp.master.asJsonObject
        val master = JsonObject.singleton("aster", masterJson.asJson)
        val regularJson = fkp.regular.map(_.asJson)
        regularJson.foreach(rj ⇒ master.add("regular", rj))
        master
      }

    val txt = listOfAccounts.asJson.spaces4

    LogHelpers.overwriteFileWith("logs/ScenarioWallets.json",txt)
  }
  
  /** We do this for multiple wallets types **/
  test("additional txn") {

    val rrs: Either[AppError, List[JsonReqRes]] = for {

      keys        ← StandardScenarios.mixedRegKeys
      script      ← StandardScenarios.NZD
      _           <- ServerOps.advanceLedger()
      trustMaster <- trustLines(keys, script, true)
      trustReg    ← trustLines(keys, script, false) // Should fail probably
      offerCreate ← keys.traverse(key ⇒ offerCreates(key)) // Use regular keys
      tranfersXrp = keys.grouped(2).toList.traverse(l ⇒ transferXrp(l.head, l(1).address))

    } yield trustMaster ::: trustReg ::: offerCreate

    rrs.foreach(LogHelpers.logAnswerToFile(s"logs/mixed_txn.json", _))
  }

  /**
    *
    * @param fundedAccounts Master AccountKeys (with correct address) of activated accounts.
    */
  def trustLines(fundedAccounts: List[FullKeyPair],
                 script: Script,
                 useMaster: Boolean): Either[AppError, List[JsonReqRes]] = {
    // What to do... maybe a TrustSet from tails to head and then transfor some IOU

    val amount: FiatAmount = FiatAmount(BigDecimal("555.666"), script)
    // Make all the accounts
    val setTrustLines: List[RippleTxnCommand[TrustSetTx]] =
      fundedAccounts.tail.map(keys ⇒ TxnFactories.genTrustLine(script.issuer, keys, amount))

    val trustlines: Either[AppError, List[JsonReqRes]] = setTrustLines.traverse { rtxc ⇒
      val signed: Either[AppError, TracedRes[SignRs]] = TraceHelpers.signOnServer(rtxc.tx, rtxc.signingKeys)
      val submitted                                   = signed.flatMap(tr ⇒ TraceHelpers.submit(tr.value))
      if (submitted.isLeft) logger.error(s"Submission Failed!")
      signed.map(_.rr)
    }

    ServerOps.advanceLedger()
    trustlines
  }

  /** We want to log the signing request/response for these so return the jsonrr is all ok */
  def offerCreates(key: FullKeyPair): Either[AppError, JsonReqRes] = {

    for {
      pays       <- StandardScenarios.tenNZD
      gets       = Drops.fromXrp(10L)
      expires    = RippleTime.fromInstant(Instant.now().plusSeconds(60 * 120))
      base       = CommonTx.withXrpFee("10")
      rq         = OfferCreateTx(key.address, Some(expires), None, gets, pays, base)
      signed     <- TraceHelpers.signOnServer(rq, key.signingKey)
      submmitted ← TraceHelpers.submit(signed.value)
    } yield signed.rr

  }

  /** We want to log the signing request/response for these so return the jsonrr is all ok
    * Signs with the regular key if present else master key.
    **/
  def transferXrp(sender: FullKeyPair, dest: AccountAddr): Either[AppError, JsonReqRes] = {
    for {
      seq        ← ServerOps.getAccountSequence(sender.address)
      txn        = TxnFactories.genXrpPayment(sender, dest, Drops.fromXrp(10000), seq)
      signed     <- TraceHelpers.signOnServer(txn, sender.signingKey)
      submmitted ← TraceHelpers.submit(signed.value)
    } yield signed.rr
  }

}
