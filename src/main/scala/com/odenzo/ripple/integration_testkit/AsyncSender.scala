package com.odenzo.ripple.integration_testkit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

import io.circe.{Json, JsonObject}

import com.odenzo.ripple.localops.utils.caterrors.CatsTransformers.{ErrorOr, ErrorOrFT}
import com.odenzo.ripple.localops.utils.caterrors.ErrorOrFT

trait RippleSender {

  def send(rq: JsonObject)(implicit ec: ExecutionContext): ErrorOrFT[JsonReqRes]

  def ask(rq: JsonObject)(implicit ec: ExecutionContext): ErrorOr[JsonReqRes]

}
