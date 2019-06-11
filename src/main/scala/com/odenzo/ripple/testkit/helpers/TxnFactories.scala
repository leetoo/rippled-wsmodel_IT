package com.odenzo.ripple.testkit.helpers

import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, JsonObject}
import io.circe.syntax._

import com.odenzo.ripple.models.utils.caterrors.AppError
import com.odenzo.ripple.models.atoms.{AccountAddr, AccountKeys, BitMaskFlag, Drops, FiatAmount, Memos, SigningPublicKey, TxnSequence}
import com.odenzo.ripple.models.wireprotocol.accountinfo.{WalletProposeRq, WalletProposeRs}
import com.odenzo.ripple.models.wireprotocol.ledgerinfo.{LedgerAcceptRq, LedgerAcceptRs}
import com.odenzo.ripple.models.wireprotocol.transactions.SubmitRs
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.{CommonTx, PaymentTx, RippleTransaction, TrustSetTx}
import com.odenzo.ripple.testkit.helpers.ServerOps.{advanceLedger, doCmdCall, doCmdCallKeepJson, genesis, getAccountSequence, serverSignAndSubmit}

object TxnFactories extends StrictLogging {

  case class RippleTxnCommand[T<:RippleTransaction](tx:T, keys:AccountKeys)
  /**
    *
    * @param issuer
    * @param account
    * @param amount
    *
    * @return List of RippleTransaction to sign and submit using the associated AccountKeys
    */
  def genTrustLine(issuer: AccountAddr, account: AccountKeys, amount: FiatAmount): RippleTxnCommand[TrustSetTx] = {
    val commonTx = CommonTx(
      fee = Some(Drops.fromXrp("100")),
      signingPubKey = Option(account.signingPubKey),
      memos = Memos.fromText("Test Transaction")
    )
    // Need to add the account setting the trust line with issuer
    RippleTxnCommand(TrustSetTx(account.address, amount, base = commonTx), account)
  }





  /**
    *
    * @param from
    * @param to
    * @param amount
    * @param sequence Required even for server signed now unless offline = false
    *                 doing server side signing Noneok
    *
    * @return
    */
  def genXrpPayment(from: AccountKeys, to: AccountAddr, amount: Drops, sequence: TxnSequence): PaymentTx = {

    val common = CommonTx(
      sequence = Some(sequence),
      fee = Some(Drops.fromXrp("50")),
      signers = None,
      signingPubKey = Some(SigningPublicKey(from.public_key_hex)),
      flags = BitMaskFlag(2147483648L),
      memos = None,
      hash = None
    )

    PaymentTx(account = from.account_id,
              amount = amount,
              destination = to,
              invoiceID = None,
              paths = None,
              sendMax = None,
              deliverMin = None,
              base = common)

  }


  /**
    * Does a wallet propose with autogenerated random seed.
    *
    * @param numWallets Number of wallet proposes to do.
    * @param keytype    ed2519 or secp256k1 as of June 2019
    */
  def makeWallets(numWallets: Int, keytype: String): Either[AppError, List[(AccountKeys, JsonReqRes)]] = {
    (1 to numWallets).toList.traverse(_ ⇒ ServerOps.makeWallet(keytype))
  }

  
  /** Creates a new account using server side WalletPropose and then initial transfer of XRP from Genesis Account
    *
    * @param amount
    * @param keyType secp256k1 or ed25519
    *
    * @return The submission results for activating the new account, or Error.
    */
  def createNewAccount(amount: Drops, keyType: String): Either[AppError, SubmitRs] = {
    val walletRq = WalletProposeRq(key_type = Some(keyType))
    val result = for {
      walletRs  ← doCmdCall(walletRq.asJsonObject, WalletProposeRs.decoder)
      keys      = walletRs.keys
      seq       <- getAccountSequence(genesis.address) // Not strictly necessary
      rq        = genXrpPayment(genesis, keys.address, amount, seq)
      submitted ← serverSignAndSubmit(rq.asJsonObject, genesis)
      _         <- advanceLedger()
    } yield submitted
    result.left.foreach(v => logger.error(s"ERROR Creating Account" + v.show))
    result
  }
}
