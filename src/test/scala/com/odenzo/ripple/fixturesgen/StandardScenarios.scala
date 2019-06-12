package com.odenzo.ripple.fixturesgen

import cats._
import cats.data._
import cats.implicits._
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.syntax._

import com.odenzo.ripple.models.atoms.{AccountAddr, AccountKeys, Currency, Drops, FiatAmount, Script}
import com.odenzo.ripple.models.utils.caterrors.AppError
import com.odenzo.ripple.models.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.{CommonTx, SetRegularKeyTx}
import com.odenzo.ripple.testkit.helpers.{FullKeyPair, ServerOps, TxnFactories}

object StandardScenarios extends StrictLogging {

  val secpWallets: Either[AppError, List[FullKeyPair]]    = initAccounts("secp256k1", 5)
  val ed25519Wallets: Either[AppError, List[FullKeyPair]] = initAccounts("ed25519", 5)

  /**Accounts with secp master keys and secp regular keys   */
  val secpsecpWallets: Either[AppError, List[FullKeyPair]] =
    initAccounts("secp256k1", 5).flatMap(setRegKey("secp256k1", _))

  /** Accounts with secp master keys and ed25519 regular keys    */
  val secpedWallets: Either[AppError, List[FullKeyPair]] = initAccounts("secp256k1", 5).flatMap(setRegKey("ed25519", _))

  /** Accounts with ed25519 master keys and secp regular keys    */
  val ededWallets: Either[AppError, List[FullKeyPair]] = initAccounts("ed25519", 5).flatMap(setRegKey("ed25519", _))

  /** Accounts with ed25519 master and ed25519 regular keys */
  val edsecpWallets: Either[AppError, List[FullKeyPair]] = initAccounts("ed25519", 5).flatMap(setRegKey("secp256k1", _))

  val mixedMasterKeys: Either[AppError, List[FullKeyPair]] = (secpWallets, ed25519Wallets).mapN(_.take(3) ::: _.take(3))

  /** Standard keys to use in a test scenario */
  val mixedRegKeys: Either[AppError, List[FullKeyPair]] = {
    // Take first two from each list. Maybe some cats magic I am missing aside from mapN
    val bag                                             = List(secpsecpWallets, secpedWallets, ededWallets, edsecpWallets).sequence
    val tops: Either[AppError, List[List[FullKeyPair]]] = Nested(bag).map(_.take(2)).value
    val merged: Either[AppError, List[FullKeyPair]]     = tops.map(v ⇒ v.flatten)
    merged
  }

  /** All the scenario accounts, in case needed for logging */
  val allScenarioAccounts: Either[AppError, List[FullKeyPair]] = {
    List(secpedWallets, ed25519Wallets, secpsecpWallets, secpedWallets, ededWallets, edsecpWallets).sequence
      .map(_.flatten)
  }

  val issuer: Either[AppError, FullKeyPair] = ed25519Wallets.map(_.head)

  val NZD: Either[AppError, Script] = issuer.map(i ⇒ Script(Currency("NZD"), i.address))

  val tenNZD: Either[AppError, FiatAmount] = NZD.map(FiatAmount(BigDecimal(10), _))

  /**
    * Crestes N wallets with specified key type. Funding all  with XRP from Genesis
    * and then advancing the ledger
    *
    * @return list of AccountKeys for created accounts.
    **/
  def initAccounts(keyType: String, numWallets: Int): Either[AppError, List[FullKeyPair]] = {
    for {
      keys <- TxnFactories.makeWallets(numWallets, keyType).map(_.map(_._1))

      // Fund all the accounts from genesis for this
      funder = ServerOps.genesis
      _ = keys.foreach { key ⇒
        ServerOps
          .getAccountSequence(funder.address)
          .map(TxnFactories.genXrpPayment(funder, key.address, Drops.fromXrp(10000), _))
          .flatMap(v ⇒ ServerOps.serverSignAndSubmit(v.asJsonObject, funder.signingKey))
      }
      _ ← ServerOps.advanceLedger()
    } yield keys
  }

  /** For the given accounts, set the regular key to the specified key type
    *
    * @return List of master and regular keys corresponding to accounts (in order)
    * */
  def setRegKey(regKeyType: String, accounts: List[FullKeyPair]): Either[AppError, List[FullKeyPair]] = {

    val base = CommonTx(fee = Some(Drops.fromXrp(50L)))
    for {
      regularKeys <- TxnFactories.makeWallets(accounts.length, regKeyType)
      regkeys     = regularKeys.map(_._1.master)
      args        = accounts.zip(regkeys)
      fullKeys    = args.map { case (m: FullKeyPair, r: AccountKeys) ⇒ m.copy(regular = Some(r)) }
      _ = fullKeys.map { fk: FullKeyPair ⇒
        val rq = SetRegularKeyTx(fk.master.address, fk.regular.map(_.address), base)
        ServerOps.serverSignAndSubmit(rq.asJsonObject, fk.master)
      }
    } yield fullKeys
  }
}
