package com.odenzo.ripple.testkit.helpers

import io.circe.{Json, JsonObject}

import com.odenzo.ripple.models.atoms.{AccountAddr, AccountKeys, RippleKeyType, RippleSeed, SigningPublicKey}
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRs, SubmitRs}
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.RippleTransaction

/** These are normally objects, but for potential  error cases keep as Json for now */
case class JsonReqRes(rq: Json, rs: Json)

object JsonReqRes {
  def empty = JsonReqRes(Json.Null, Json.Null)
}

case class TracedRes[T](value: T, rr: JsonReqRes)

/* Has master key and optional regular key */
case class FullKeyPair(master: AccountKeys, regular: Option[AccountKeys]) {

  def signingKey: AccountKeys = regular.getOrElse(master)

  /** Always returns the account address from the master seed */
  def address: AccountAddr = master.address

  /** Returns the regular key seed if exists else master key seed */
  def seed: RippleSeed = signingKey.master_seed

  def singingPubKey: SigningPublicKey = signingKey.signingPubKey

  def hasRegular: Boolean = regular.isDefined

  def masterType: RippleKeyType = master.key_type

  def regularType: Option[RippleKeyType] = regular.map(_.key_type)
}

object FullKeyPair {

  def apply(master: AccountKeys): FullKeyPair                   = FullKeyPair(master, None)
  def apply(master: AccountKeys, reg: AccountKeys): FullKeyPair = FullKeyPair(master, Some(reg))
}
