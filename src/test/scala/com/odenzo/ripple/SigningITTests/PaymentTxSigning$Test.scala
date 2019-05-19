package com.odenzo.ripple.SigningITTests

import java.nio.file.Paths

import cats.implicits._
import com.odenzo.ripple.fixturesgen.ScenerioBuilder
import com.odenzo.ripple.integration_tests.integration_testkit.IntegrationTestFixture
import com.odenzo.ripple.models.atoms.Drops
import com.odenzo.ripple.models.support.{Commands, RippleCodecUtils}
import com.odenzo.ripple.models.wireprotocol.serverinfo.{FeeRq, FeeRs}
import com.odenzo.ripple.localops.utils.caterrors.CatsTransformers.ErrorOr
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder, Json}
import org.scalatest.{EitherValues, FunSuite, Matchers}


/** Attempt to create a ripple transaction, locally sign and submit
*  All very manual while get it to work.
  */
class PaymentTxSigning$Test extends FunSuite with IntegrationTestFixture {

  test("Secp 2 secp") {
    for {
    wallets <- ScenerioBuilder.wallets
    sender = wallets.head
    recv =  wallets.slice(1, 2).head
    seq <- ScenerioBuilder.getAccountSequence(sender.address)
    txn =  ScenerioBuilder.createXrpTransfer(sender,recv,Drops(1000),seq)

    } yield wallets


  }
}
