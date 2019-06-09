package com.odenzo.ripple.testkit.helpers

import io.circe.Json


/** These are normally objects, but for potential  error cases keep as Json for now */
case class JsonReqRes(rq:Json, rs:Json)

/** Has the Request Response Json and the decoded result, used for Transaction and Commands */
case class TraceRes[T](rr:JsonReqRes, result:T)
