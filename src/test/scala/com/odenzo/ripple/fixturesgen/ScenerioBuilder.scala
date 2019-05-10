package com.odenzo.ripple.fixturesgen

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Json}

import com.odenzo.ripple.integration_testkit.RequestResponse
import com.odenzo.ripple.models.wireprotocol.accountinfo.{WalletProposeRq, WalletProposeRs}
import com.odenzo.ripple.utils.CirceUtils
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr
import cats._
import cats.data._
import cats.implicits._
import io.circe.syntax._

import com.odenzo.ripple.models.atoms.{AccountAddr, AccountKeys, Drops}
import com.odenzo.ripple.models.support.{
  GenesisAccount,
  RippleGenericError,
  RippleGenericResponse,
  RippleGenericSuccess
}
import com.odenzo.ripple.models.wireprotocol.ledgerinfo.{LedgerAcceptRq, LedgerAcceptRs}
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRq, SignRs, SubmitRq, SubmitRs}
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.{CommonTx, PaymentTx}
import com.odenzo.ripple.utils.caterrors.{AppError, OError}

/**
  * Some helpers to create ripple test-net server scenario.
  * Use on local test-net, ideally be able to re-run each time.
  */
object ScenerioBuilder extends StrictLogging with FixtureGeneratorUtils {

  val (wallets: scala.List[_root_.com.odenzo.ripple.models.atoms.AccountKeys],
       walletJson: List[RequestResponse[Json, Json]]) = makeWallets()

  val genesis: AccountKeys = wallets.head

  xrpTransferScenario()

  def xrpTransferScenario(): Unit = {
    val others = wallets.drop(1)
    others.foreach(w ⇒ logger.info(s"Other Wallet: $w"))
    initialActivation(others, Drops.fromXrp(10000))
    advanceLedger()
  }

  /** Tick over ledger, this must be duplicated N times already! */
  def advanceLedger(): Unit = {
    val rs: LedgerAcceptRs = getOrLog(doTxnCall(LedgerAcceptRq().asJson, LedgerAcceptRs.decoder))
    logger.info(s"Advanced Ledger: $rs")
  }

  /**
    * Create 2 each  sepc256k1 and ed25199 accounts
    * Use Genesis to fund them all with 10k XRP
    *
    */
  def makeWallets(): (List[AccountKeys], List[RequestResponse[Json, Json]]) = {
    val cmds = List(
      WalletProposeRq(None, passphrase = Some("masterpassphrase"), Some("secp256k1")),
      WalletProposeRq(None, None, Some("secp256k1")),
      WalletProposeRq(None, None, Some("secp256k1")),
      WalletProposeRq(None, None, Some("ed25519")),
      WalletProposeRq(None, None, Some("ed25519"))
    )

    val wallets: Either[AppError, List[(AccountKeys, RequestResponse[Json, Json])]] = cmds.traverse { rq ⇒
      val jsonRq: Json = rq.asJson
      // Do manually to save the json req res
      val rr: ErrorOr[RequestResponse[Json, Json]] = doCall(jsonRq)
      val res: Either[AppError, WalletProposeRs]   = rr.flatMap(v ⇒ parseTxnReqRes(v, Decoder[WalletProposeRs]))
      res.map(_.keys).product(rr)
    }

    val tupled: List[(AccountKeys, RequestResponse[Json, Json])] = getOrLog(wallets)
    (tupled.map(_._1), tupled.map(_._2))

  }

  /**
    *  Transfers money to each account and advances one ledger
    * @param toAct
    * @param xrp
    */
  def initialActivation(toAct: List[AccountKeys], xrp: Drops): Unit = {
    logger.info(s"Activated ${toAct.length} accounts with $xrp")
    val signrqs: List[Json] = toAct.map { keys ⇒
      val json = createXrpTransfer(genesis, keys, xrp).asJson
      SignRq(json, genesis.secret).asJson
    }

    val done: List[SubmitRs] = signrqs.map { signRq ⇒
      val signed: SignRs = getOrLog(doTxnCall(signRq, Decoder[SignRs]))
      val subRq          = SubmitRq(signed.tx_blob).asJson
      val submitted      = getOrLog(doTxnCall(subRq, Decoder[SubmitRs]))
      submitted.engine.engine_result shouldEqual "tesSUCCESS"
      submitted
    }
    advanceLedger()
  }

  def createXrpTransfer(from: AccountKeys, to: AccountKeys, amount: Drops): PaymentTx = {
    val base = CommonTx()
    PaymentTx(from.account_id, amount, to.account_id, None, None, None, None, base)

  }

}
