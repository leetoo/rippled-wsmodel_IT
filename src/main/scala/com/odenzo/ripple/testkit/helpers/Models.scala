package com.odenzo.ripple.testkit.helpers

import io.circe.{Json, JsonObject}

import com.odenzo.ripple.models.atoms.AccountKeys


/** These are normally objects, but for potential  error cases keep as Json for now */
case class JsonReqRes(rq:Json, rs:Json)

/** Has the Request Response Json and the decoded result, used for Transaction and Commands */
case class TraceRes[T](rr:JsonReqRes, result:T)


trait RippleInstruction
case class  RippleTxnCommand(txjson:JsonObject, keys:AccountKeys) extends RippleInstruction
case class  RippleCommand(rq:JsonObject) extends RippleInstruction
