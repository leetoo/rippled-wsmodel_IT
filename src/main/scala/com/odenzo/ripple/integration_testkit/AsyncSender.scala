package com.odenzo.ripple.integration_testkit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

import io.circe.Json

import com.odenzo.ripple.utils.caterrors.CatsTransformers.{ErrorOr, ErrorOrFT}
import com.odenzo.ripple.utils.caterrors.ErrorOrFT

trait RippleSender {

  def send(rq: Json)(implicit ec: ExecutionContext): ErrorOrFT[RequestResponse[Json, Json]]

  def ask(rq: Json)(implicit ec: ExecutionContext): ErrorOr[RequestResponse[Json, Json]]

}
