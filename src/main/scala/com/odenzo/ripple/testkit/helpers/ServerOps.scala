package com.odenzo.ripple.testkit.helpers

import com.typesafe.scalalogging.StrictLogging
import io.circe.syntax._
import io.circe.{Decoder, JsonObject}

import com.odenzo.ripple.models.utils.caterrors.AppError
import com.odenzo.ripple.models.atoms.{AccountAddr, AccountKeys, TxBlob, TxnSequence}
import com.odenzo.ripple.models.support.GenesisAccount
import com.odenzo.ripple.models.utils.CirceUtils
import com.odenzo.ripple.models.wireprotocol.accountinfo.{AccountInfoRq, AccountInfoRs, WalletProposeRq, WalletProposeRs}
import com.odenzo.ripple.models.wireprotocol.ledgerinfo.{LedgerAcceptRq, LedgerAcceptRs}
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRq, SignRs, SubmitRq, SubmitRs}

/**
  * High-Level functions to interact with a Ripple server (and sometimes use local signing )
  */
object ServerOps extends StrictLogging with LogHelpers with ServerCallUtils {

  /** Genesis is a secp account with no regular keys */
  val genesis: FullKeyPair = FullKeyPair(GenesisAccount.accountKeys, None)

 
  

  /**
    *    Note: This does not automatically advance the ledger when in stand-alone mode.
    * @param tx_json Transaction to sign and submit. Since server side auto-fillable ok but not recommended
    * @param sig
    * @return
    */
  def serverSignAndSubmit[T](tx_json: JsonObject, sig: AccountKeys): Either[AppError, SubmitRs] = {
    logger.info(s"Secret: $sig")
    for {
      sign   ← serverSign(tx_json, sig)
      submit ← submitTxn(sign.tx_blob)
    } yield submit
  }

  /** Signs the given tx_json on a Ripple server */
  def serverSign(tx_json: JsonObject, sig: AccountKeys): Either[AppError, SignRs] = {
    val toSign = SignRq(tx_json.asJson, sig.master_seed, false, key_type = sig.key_type.v)
    val msgObj = CirceUtils.pruneNullFields(toSign.asJsonObject)
    logger.debug(s"Sending Sign CMD: \n ${msgObj.asJson.spaces4}")
    doCmdCall(msgObj, SignRs.decoder2)
  }

  /** Submits a fully signed transaction, represented as txblob */
  def submitTxn[T](txblob: TxBlob): Either[AppError, SubmitRs] = {
    val rq = SubmitRq(txblob, fail_hard = true)
    for {
      rr ← callServer(rq.asJsonObject)
      _  = logger.debug(s"RR: ${reqres2string(rr)}")
      rs ← decodeTxnCall(rr, Decoder[SubmitRs])
    } yield rs
  }

  /** Submit a command that gives a direct response as opposed to a transaction */
  def submitCmd[A](rq:JsonObject, cmdDecoder:Decoder[A]): Either[AppError, A] = {
    doCmdCall(rq, cmdDecoder)
  }
  
  /** Gets the latest account sequence for use in populating transactions */
  def getAccountSequence(address: AccountAddr): Either[AppError, TxnSequence] = {
    val rq                                   = AccountInfoRq(address, queue = false, signer_lists = false, strict = true)
    val ans: Either[AppError, AccountInfoRs] = doCmdCall(rq.asJsonObject, AccountInfoRs.decoder)
    ans.map { rs ⇒
      val s = rs.account_data.sequence
      logger.info(s"Account ${address} Sequence: $s")
      s
    }
  }

  def makeWallet(keytype: String = "ed25519"): Either[AppError, (FullKeyPair, JsonReqRes)] = {
    val rq = WalletProposeRq(key_type = Some(keytype))
    doCmdCallKeepJson[WalletProposeRs](rq.asJsonObject, WalletProposeRs.decoder)
    .map{ case (rs, rr) => (FullKeyPair(rs.keys,None), rr) }

  }


  /** Tick over ledger when it is in stand-alone mode */
  def advanceLedger(): Either[AppError, LedgerAcceptRs] = {
    val rs = doCmdCall(LedgerAcceptRq().asJsonObject, LedgerAcceptRs.decoder)
    rs
  }

}
