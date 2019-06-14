package com.odenzo.ripple.fixturesgen

import java.time.Instant
import scala.util.Random

import cats.data.Nested
import io.circe.{Decoder, Json, JsonObject}
import io.circe.syntax._
import org.scalatest.FunSuite

import com.odenzo.ripple.testkit.helpers.StandardScenarios._
import com.odenzo.ripple.integration_testkit.{IntegrationTestFixture, MsgLenses}
import com.odenzo.ripple.models.atoms.RippleTxnType.OfferCreate
import com.odenzo.ripple.models.atoms.{
  AccountAddr,
  AccountKeys,
  Currency,
  Drops,
  FiatAmount,
  Memos,
  RippleTime,
  Script,
  TxnSequence
}
import com.odenzo.ripple.models.utils.CirceUtils
import com.odenzo.ripple.models.utils.caterrors.AppError
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.{
  CommonTx,
  OfferCancelTx,
  OfferCreateTx,
  TrustSetTx
}
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRq, SignRs, SubmitRq}
import com.odenzo.ripple.testkit.helpers.TxnFactories.RippleTxnCommand
import com.odenzo.ripple.testkit.helpers.{
  FullKeyPair,
  JsonReqRes,
  LogHelpers,
  ServerCallUtils,
  ServerOps,
  StandardScenarios,
  TraceHelpers,
  TracedRes,
  TxnFactories
}

/**
  * First fixture is to create some accounts and send a series of transactions that are saved to disk.
  * The txn send from both ed25519 and secp256k1 accounts.
  * More txn types to be added incrementally.
  **/
class SigningTxnFixtureMakerTest extends FunSuite with IntegrationTestFixture {

  import CirceUtils._
  import cats.implicits._

  val autofillBase = CommonTx()
  val baseTx = CommonTx(fee=Some(Drops.fromXrp(100))) // MsgLenses
  test("Dump Scenario Accounts") {
    val accounts: List[FullKeyPair] = getOrLog(StandardScenarios.allScenarioAccounts)
    val listOfAccounts: List[JsonObject] = accounts.map { fkp ⇒
      val masterJson  = fkp.master.asJsonObject
      val master      = JsonObject.singleton("master", masterJson.asJson)
      val regularJson = fkp.regular.map(_.asJson)
      regularJson.foreach(rj ⇒ master.add("regular", rj))
      master
    }

    val txt = listOfAccounts.asJson.spaces4

    LogHelpers.overwriteFileWith("logs/scenarioKeys.json", txt)
  }

  /**
    * Missing PathSet which is odd one, so worth testing using FiatTransfer
    * @param rawKeys An even amount of keys or the first one will be dropped
    * @return
    */
  def additionalTxn(rawKeys: List[FullKeyPair]): Either[AppError, List[JsonReqRes]] = {
    val keys = if (rawKeys.length % 2 == 1) rawKeys.drop(1) else rawKeys
    for {
      script        ← StandardScenarios.NZD
      _             <- ServerOps.advanceLedger()
      trustMaster   <- trustLines(keys, script, true)
      trustReg      ← trustLines(keys, script, false) // Should fail probably
      offers        ← offersCreateAndCancel(keys)
      transferXrp   <- keys.grouped(2).toList.traverse(l ⇒ transferXrp(l.head, l(1).address, Drops(500), None))
      transferMemos ← xrpTransferWithMemo(keys)
      _             <- ServerOps.advanceLedger()

    } yield transferXrp ::: transferMemos ::: trustMaster ::: trustReg ::: offers

  }

  /** We do this for multiple wallets types **/
  test("additional txn by keytype") {

    val keys: List[FullKeyPair] = getOrLog(StandardScenarios.allScenarioAccounts)
    val resultsByKeyType: Map[String, List[JsonReqRes]] = keys
      .groupBy { fkp ⇒
        val master  = fkp.master.key_type.v
        val regular = fkp.regular.map(r ⇒ s"_regular_${r.key_type.v}").getOrElse("_none")
        master + regular
      }
      .mapValues(k ⇒ getOrLog(additionalTxn(k)))

    resultsByKeyType.foreach { case (cat, res) ⇒ LogHelpers.logAnswerToFile(s"logs/${cat}_txns.json", res) }
    val all = resultsByKeyType.values.flatten.toList
    LogHelpers.logAnswerToFile(s"logs/all_txns.json", all)
  }

  /** Transfer from first couple account to genesis with a memo */
  def xrpTransferWithMemo(keys: List[FullKeyPair]): Either[AppError, List[JsonReqRes]] = {
    val memos: Option[Memos] = Memos.fromText("Hello World " + Instant.now.toString)
    keys.take(2).traverse { fkp ⇒
      val res: Either[AppError, JsonReqRes] = transferXrp(fkp, GENESIS.address, Drops(666), memos)
      res
    }
  }

  /**
    * Note the requests are not auto-filled.
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
  def offersCreateAndCancel(keys: List[FullKeyPair]): Either[AppError, List[JsonReqRes]] = {
    val created: Either[AppError, List[(OfferCreateTx, FullKeyPair, JsonReqRes)]] = keys.traverse { key ⇒
      for {
        seq ← ServerOps.getAccountSequence(key.address)
        pays       <- StandardScenarios.tenNZD
        gets       = Drops.fromXrp(10L)
        expires    = RippleTime.fromInstant(Instant.now().plusSeconds(60 * 120))
        base       = CommonTx.lensSequence.set(Some(seq))(baseTx)
        rq         = OfferCreateTx(key.address, Some(expires), None, gets, pays, base)
        signed     <- TraceHelpers.signOnServer(rq, key.signingKey)
        submmitted ← TraceHelpers.submit(signed.value)
      } yield (rq, key, signed.rr)
    }

    ServerOps.advanceLedger()

    val allRR = created.flatMap { ll ⇒
      ll.flatTraverse {
        case (createTx: OfferCreateTx, fkp: FullKeyPair, createRR: JsonReqRes) ⇒
          val cancel = OfferCancelTx(createTx.account, createTx.base.sequence.get, autofillBase)
          for {
            signed    <- TraceHelpers.signOnServer(cancel, fkp.signingKey)
            submitted <- TraceHelpers.submit(signed.value)
          } yield List(signed.rr, createRR)
      }
    }
    ServerOps.advanceLedger()
    allRR
  }


  /** We want to log the signing request/response for these so return the jsonrr is all ok
    * Signs with the regular key if present else master key.
    **/
  def transferXrp(sender: FullKeyPair,
                  dest: AccountAddr,
                  amount: Drops,
                  memos: Option[Memos] = None): Either[AppError, JsonReqRes] = {
    for {
      seq        ← ServerOps.getAccountSequence(sender.address)
      txn        = TxnFactories.genXrpPayment(sender, dest, amount, seq)
      withMemo   = MsgLenses.lensTxMemo.set(memos)(txn)
      signed     <- TraceHelpers.signOnServer(withMemo, sender.signingKey)
      submmitted ← TraceHelpers.submit(signed.value)
    } yield signed.rr
  }

}
