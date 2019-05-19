package com.odenzo.ripple.fixturesgen

import cats._
import cats.data._
import cats.implicits._
import com.odenzo.ripple.integration_testkit.RequestResponse
import com.odenzo.ripple.models.atoms.{
  AccountAddr,
  AccountKeys,
  BitMaskFlag,
  Drops,
  Flag,
  SigningPublicKey,
  TxnSequence
}
import com.odenzo.ripple.models.wireprotocol.accountinfo.{
  AccountInfoRq,
  AccountInfoRs,
  WalletProposeRq,
  WalletProposeRs
}
import com.odenzo.ripple.models.wireprotocol.ledgerinfo.{LedgerAcceptRq, LedgerAcceptRs}
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.{CommonTx, PaymentTx}
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRq, SignRs, SubmitRq, SubmitRs}
import com.odenzo.ripple.localops.utils.caterrors.AppError
import com.odenzo.ripple.localops.utils.caterrors.CatsTransformers.ErrorOr
import com.typesafe.scalalogging.StrictLogging
import io.circe.syntax._
import io.circe.{Decoder, Json}

/**
  * Some helpers to create ripple test-net server scenario.
  * Use on local test-net, ideally be able to re-run each time.
  * This assumes a local docket normally, and sets up some base accounts.
  */
object ScenerioBuilder extends StrictLogging with FixtureGeneratorUtils {

  private val walletsVals: Either[AppError, (List[AccountKeys], List[RequestResponse[Json, Json]])] = makeWallets()

  val wallets: Either[AppError, List[AccountKeys]]                    = walletsVals.map(_._1.drop(1))
  val walletJson: Either[AppError, List[RequestResponse[Json, Json]]] = walletsVals.map(_._2.drop(1))
  val genesis: Either[AppError, AccountKeys]                          = walletsVals.map(v => v._1.head)
  val genesisJson: Either[AppError, RequestResponse[Json, Json]]      = walletsVals.map(v => v._2.head)

  xrpTransferScenario()

  def xrpTransferScenario(): Either[AppError, LedgerAcceptRs] = {
    wallets.flatMap { wlist =>
      val ws: List[AccountKeys] = wlist
      val activated             = initialActivation(ws, Drops.fromXrp(10000))
      if (activated.isRight) {
        logger.warn(s"Activated addresses :\n\n${ws.map(_.address).mkString("\n", "\n", "\n")} \n\n")
      } else {
        logger.error(s"Initial Activation Failed ${activated.left}")
      }
      advanceLedger()
    }
  }

  /** Tick over ledger, this must be duplicated N times already! */
  def advanceLedger(): Either[AppError, LedgerAcceptRs] = {
    val rs = doCmdCall(LedgerAcceptRq().asJson, LedgerAcceptRs.decoder)
    rs
  }

  def getAccountSequence(address: AccountAddr): Either[AppError, TxnSequence] = {
    val rq                                   = AccountInfoRq(address, queue = false, signer_lists = false, strict = true)
    val ans: Either[AppError, AccountInfoRs] = doCmdCall(rq.asJson, AccountInfoRs.decoder)
    ans.map(_.account_data.sequence)
  }

  def createNewAccount(funder: AccountKeys, amount: Drops, keyType: String) = {}

  /**
    * First wallet creation is always genesis account, which exists already.
    * Create 2 each  sepc256k1 and ed25199 accounts
    *
    *
    */
  def makeWallets(): Either[AppError, (List[AccountKeys], List[RequestResponse[Json, Json]])] = {
    val cmds = List(
      WalletProposeRq(None, passphrase = Some("masterpassphrase"), Some("secp256k1")),
      WalletProposeRq(None, None, Some("secp256k1")),
      WalletProposeRq(None, None, Some("secp256k1")),
      WalletProposeRq(None, None, Some("ed25519")),
      WalletProposeRq(None, None, Some("ed25519"))
    )

    cmds
      .traverse { rq ⇒
        doCmdCallKeepJson[WalletProposeRs](rq.asJson, WalletProposeRs.decoder)
          .fmap {
            case (rs, rr) =>
              (rs.keys, rr)
          }
      }
      .fmap(_.unzip)

  }

  /** We want a fully formed offline transaction here
    * w */
  def createXrpTransfer(from: AccountKeys, to: AccountKeys, amount: Drops, sequence: TxnSequence): PaymentTx = {

    val common = CommonTx(
      sequence = Some(sequence),
      fee = Some(Drops.fromXrp("55")),
      signers = None,
      signingPubKey = Some(SigningPublicKey(from.public_key_hex)),
      flags = BitMaskFlag(2147483648L),
      memos = None,
      hash = None
    )

    PaymentTx(account = from.account_id,
              amount = amount,
              destination = to.account_id,
              invoiceID = None,
              paths = None,
              sendMax = None,
              deliverMin = None,
              base = common)

  }

  /** Use Genesis to fund them all with 10k XRP */
  def activateAccount(keys: AccountKeys, xrp: Drops): Either[AppError, SubmitRs] = {
    logger.info(s"Activated Account ${keys.address}")
    val res: Either[AppError, SubmitRs] = for {
      sender    <- genesis
      seq       <- getAccountSequence(sender.address)
      rq        = createXrpTransfer(sender, keys, xrp, seq)
      toSign    = SignRq(rq.asJson, sender.secret)
      signed    <- doCmdCall(toSign.asJson, SignRs.decoder2)
      toSubmit  = SubmitRq(signed.tx_blob).asJson
      submitted <- doTxnCall(toSubmit.asJson, Decoder[SubmitRs])
      _         <- advanceLedger()
    } yield submitted

    res.left.foreach(v => logger.error(s"ERROR Activating Account ${keys.address} " + v.show))
    res
  }

  /**
    *  Transfers money to each account and advances one ledger
    *
    * @param toAct
    * @param xrp
    */
  def initialActivation(toAct: List[AccountKeys], xrp: Drops): Either[AppError, List[SubmitRs]] = {
    logger.info("Activating Accounts: " + toAct.map(_.address).mkString("\n", "\n", "\n"))
    val res: Either[AppError, List[SubmitRs]] = toAct.traverse(activateAccount(_, xrp))

    logger.warn(s"INIITAL ACTIVATION COMPLETED OF NEW ACCOUNTS OK = ${res.isRight}")
    res
  }

}
