package com.odenzo.ripple.testkit.comms

import scala.concurrent.ExecutionContext

import io.circe.JsonObject

import com.odenzo.ripple.models.utils.caterrors.CatsTransformers.{ErrorOr, ErrorOrFT}
import com.odenzo.ripple.testkit.helpers.JsonReqRes

trait RippleSender {

  def send(rq: JsonObject)(implicit ec: ExecutionContext): ErrorOrFT[JsonReqRes]

  def ask(rq: JsonObject)(implicit ec: ExecutionContext): ErrorOr[JsonReqRes]

}
