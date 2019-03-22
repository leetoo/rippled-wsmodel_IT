package com.odenzo.ripple.test_utils

import java.nio.file.Paths

import cats._
import cats.data._
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, Json}
import org.scalatest.{EitherValues, FunSuite, Matchers}

import com.odenzo.ripple.models.atoms.AccountAddr
import com.odenzo.ripple.models.support.{Commands, RippleCodecUtils}
import com.odenzo.ripple.models.wireprotocol.accountinfo.WalletProposeRq
import com.odenzo.ripple.models.wireprotocol.serverinfo.{FeeRq, FeeRs}
import com.odenzo.ripple.utils.CirceUtils
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr
import com.odenzo.ripple.utils.caterrors.AppException

class RippleCommands$Test extends FunSuite with EitherValues with Matchers with StrictLogging {

  val base = Paths.get("/private/tmp/traces/")

  test("Loading") {
    val cmd = Commands.AccountInfo.FeeCmd
    val subdir = "com.odenzo.ripple.models.wireprotocol.serverinfo.FeeRq_1"
    val dir = base.resolve(subdir)
    logger.info(s"Looking at $dir")
    val answer: ErrorOr[(Json, Json, AnyRef, AnyRef)] = TestTracingAndLoading.readTraceFiles(dir, "bpp")
    answer.left.foreach { err ⇒
      logger.error(s"Problem ${err.show}")
    }
    answer.isRight shouldBe true

    val decoder = Decoder[FeeRs]
    val encoder = Encoder[FeeRq]

    answer.right.value match {
      case (rq, rs, a, b) ⇒
        logger.info(s"Request  JSON:\n ${rq.spaces4}")
        logger.info(s"Response JSON:\n ${rs.spaces4}")

        val rqObj: FeeRq = a.asInstanceOf[FeeRq]
        val rsObj = b.asInstanceOf[FeeRs]

        val encoded: Json = encoder(rqObj)
        val decoded: ErrorOr[FeeRs] = RippleCodecUtils.decodeFullyOnSuccess(rs, decoder)

        decoded.left.foreach { err ⇒
          logger.error(s"Trouble Decoding Result JSON ${err.show}")
        }

        encoded shouldEqual rq
        decoded.right.value shouldEqual b
    }
  }

}
