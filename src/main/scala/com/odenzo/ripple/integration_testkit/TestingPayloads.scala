package com.odenzo.ripple.integration_testkit

import cats._
import cats.data._
import cats.implicits._
import io.circe.Json

import com.odenzo.ripple.models.support.{RippleRq, RippleRs}

/**
  *
  * @param rq
  * @param rs
  * @tparam A
  * @tparam B
  */
case class RequestResponse[A, B](rq: A, rs: B)

object RequestResponse {
  type RAW = RequestResponse[Json, Json]
  implicit val show: Show[RAW] = Show.show[RAW](s ⇒ s"Req: ${s.rq.show}\n Res: ${s.rs.show}")
}

case class DecodeResult[A <: RippleRq, B <: RippleRs](raw: RequestResponse[Json, Json], objs: RequestResponse[A, B])

class TestingPayloads {

  def RawRqRs(rq: Json): Ior[Json, Json] = Ior.left[Json, Json](rq)

  val both: Ior[Json, Json] = RawRqRs(Json.fromString("foo")).putRight(Json.fromString("bar"))
}
