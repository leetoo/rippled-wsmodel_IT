package com.odenzo.ripple.testkit.helpers

import io.circe.{Json, JsonObject}

import com.odenzo.ripple.models.atoms.AccountKeys
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRs, SubmitRs}
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.RippleTransaction

/** These are normally objects, but for potential  error cases keep as Json for now */
case class JsonReqRes(rq: Json, rs: Json)

object JsonReqRes {
  def empty = JsonReqRes(Json.Null, Json.Null)
}

case class TracedRes[T](value:T, rr:JsonReqRes)

