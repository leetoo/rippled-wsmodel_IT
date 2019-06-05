//package com.odenzo.ripple.SigningITTests
//
//import java.nio.file.Paths
//
//import cats.implicits._
//
//import com.odenzo.ripple.fixturesgen.{FixtureGeneratorUtils, ScenerioBuilder}
//import com.odenzo.ripple.integration_tests.integration_testkit.IntegrationTestFixture
//import com.odenzo.ripple.models.atoms.{Drops, TxBlob}
//import com.odenzo.ripple.models.support.{Commands, RippleCodecUtils}
//import com.odenzo.ripple.models.wireprotocol.serverinfo.{FeeRq, FeeRs}
//import com.odenzo.ripple.localops.utils.caterrors.CatsTransformers.ErrorOr
//import com.typesafe.scalalogging.StrictLogging
//import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
//import io.circe.syntax._
//import org.scalatest.{EitherValues, FunSuite, Matchers}
//
//import com.odenzo.ripple.integration_testkit.JsonReqRes
//import com.odenzo.ripple.localops.RippleLocalAPI
//import com.odenzo.ripple.localops.utils.{CirceUtils, JsonUtils}
//import com.odenzo.ripple.localops.utils.caterrors.AppError
//import com.odenzo.ripple.models.wireprotocol.transactions.{SignRq, SignRs, SubmitRq, SubmitRs}
//import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.PaymentTx
//
///** Attempt to create a ripple transaction, locally sign and submit
//  *  All very manual while get it to work. Note that each TxnSignature will be different,
//  *  resulting in different tx_blob. So we test correctness to start by 'submit'iing a locally signed
//  *  transaction.
//  */
//class PaymentTxSigning$Test extends FunSuite with IntegrationTestFixture with JsonUtils with FixtureGeneratorUtils {
//
//  val wallets = ScenerioBuilder.wallets // This will trigger a few tranasctions which are logged
//
//
//  test("Secp 2 secp") {
//    val res = for {
//      wallets    <- ScenerioBuilder.wallets
//      sender     <- ScenerioBuilder.genesis
//      recv       = wallets.head
//      seq        <- ScenerioBuilder.getAccountSequence(sender.address)
//      txn        = ScenerioBuilder.createXrpTransfer(sender, recv.address, Drops(1000000000), seq)
//      txjsonFull = txn.asJsonObject
//      txjson     = CirceUtils.pruneNullFields(txjsonFull)
//      _          = logger.info(s"TxJson Rq: ${print(txjson.asJson)}")
//      _          = logger.info(s"Master Seed ${sender.master_seed.v.v}")
//      _          = logger.info(s"Public Key Hex ${sender.public_key_hex}")
//
//      signRq       = SignRq(txjson.asJson, sender.secret, offline = false)
//      serverSigned ← doCmdCallKeepJson(signRq.asJsonObject, SignRs.decoder2)
//      serverTxBlob = serverSigned._1.tx_blob
//      serverSubmission ← submit(serverTxBlob)
//    } yield serverSubmission
//
//    val submitRs = getOrLog(res)
//  }
//
//  // Singature seems to be always 140 in example and server calls.
//
//  test("Replaying An Initial Transaction") {}
//}
