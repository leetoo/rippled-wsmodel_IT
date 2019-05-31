package com.odenzo.ripple.SigningITTests

import java.nio.file.Paths

import cats.implicits._

import com.odenzo.ripple.fixturesgen.{FixtureGeneratorUtils, ScenerioBuilder}
import com.odenzo.ripple.integration_tests.integration_testkit.IntegrationTestFixture
import com.odenzo.ripple.models.atoms.{Drops, TxBlob}
import com.odenzo.ripple.models.support.{Commands, RippleCodecUtils}
import com.odenzo.ripple.models.wireprotocol.serverinfo.{FeeRq, FeeRs}
import com.odenzo.ripple.localops.utils.caterrors.CatsTransformers.ErrorOr
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import io.circe.syntax._
import org.scalatest.{EitherValues, FunSuite, Matchers}

import com.odenzo.ripple.integration_testkit.JsonReqRes
import com.odenzo.ripple.localops.RippleLocalAPI
import com.odenzo.ripple.localops.utils.JsonUtils
import com.odenzo.ripple.localops.utils.caterrors.AppError
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRq, SignRs, SubmitRq, SubmitRs}
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.PaymentTx

/** Attempt to create a ripple transaction, locally sign and submit
  *  All very manual while get it to work. Note that each TxnSignature will be different,
  *  resulting in different tx_blob. So we test correctness to start by 'submit'iing a locally signed
  *  transaction.
  */
class PaymentTxSigning$Test extends FunSuite with IntegrationTestFixture with JsonUtils with FixtureGeneratorUtils {

  val wallets = ScenerioBuilder.wallets // This will trigger a few tranasctions which are logged

  def submit(txblob: TxBlob): Either[AppError, SubmitRs] = {
    val rq = SubmitRq(txblob, fail_hard = true)
    logger.info(s"Submit Rq: \n ${print(rq.asJsonObject)}")
    for {
      rr ← doCall(rq.asJsonObject)
      _  = logger.info(s"Submit Conversation ${rr.show}")
      rs ← decodeTxnCall(rr, Decoder[SubmitRs])
    } yield rs

  }
  test("Secp 2 secp") {
    val res = for {
      wallets    <- ScenerioBuilder.wallets
      sender     <- ScenerioBuilder.genesis
      recv       = wallets.head
      seq        <- ScenerioBuilder.getAccountSequence(sender.address)
      txn        = ScenerioBuilder.createXrpTransfer(sender, recv, Drops(1000000000), seq)
      txjsonFull ← json2object(Encoder[PaymentTx].apply(txn))
      txjson     = pruneNullFields(txjsonFull)
      _          = logger.info(s"TxJson Rq: ${print(txjson.asJson)}")
      _          = logger.info(s"Master Seed ${sender.master_seed.v.v}")
      _          = logger.info(s"Public Key Hex ${sender.public_key_hex}")

      signRq       = SignRq(txjson.asJson, sender.secret, offline = false)
      serverSigned ← doCmdCallKeepJson(signRq.asJsonObject, SignRs.decoder2)
      serverTxBlob = serverSigned._1.tx_blob
      serverSubmit = SubmitRq(serverTxBlob)

      _            = logger.info("Server SignTest: " + serverSigned._2.show)

      signedTxBlob ← RippleLocalAPI.sign(txjson, sender.master_seed.v.v)
      submission   <- submit(TxBlob(signedTxBlob))
      _            = logger.info(s"Submission Result: $submission")    // This fails with bad signature

      serverSubmission ← submit(serverTxBlob)

    } yield submission

    val submitRs = getOrLog(res)
  }

  // Singature seems to be always 140 in example and server calls.

  test("Replaying An Initial Transaction") {}
}
