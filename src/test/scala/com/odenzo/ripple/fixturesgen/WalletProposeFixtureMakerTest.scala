package com.odenzo.ripple.fixturesgen

import java.security.SecureRandom
import java.util.UUID

import cats.implicits._
import io.circe.syntax._
import org.scalatest.FunSuite
import org.scalatest.concurrent.PatienceConfiguration

import com.odenzo.ripple.models.wireprotocol.accountinfo.{WalletProposeRq, WalletProposeRs}
import com.odenzo.ripple.localops.utils.caterrors.AppError

class WalletProposeFixtureMakerTest extends FunSuite with FixtureGeneratorUtils with PatienceConfiguration {


  val encoder = WalletProposeRq.encoder
  val decoder = WalletProposeRs.decoder



  val secureRND = SecureRandom.getInstanceStrong

  def makePassword(): String = {
    secureRND.generateSeed(4) // 32 bit int
    UUID.randomUUID().toString
  }

  def secpPasswordFixture: List[WalletProposeRq] = {
    val genesis = WalletProposeRq(None, Some("masterpassphrase"), Some("secp256k1"))
    val auto    = (1 to 10).map(_ ⇒ WalletProposeRq(None, None, Some("secp256k1")))
    val uuid = (1 to 10).map(_ ⇒ WalletProposeRq(None, Some(makePassword()), Some("secp256k1")))
    genesis :: auto.toList ::: uuid.toList
  }

  def ed25519PasswordFixture: List[WalletProposeRq] = {
    val auto = (1 to 10).map(_ ⇒ WalletProposeRq(None, None, Some("ed25519")))
    val uuid = (1 to 10).map(_ ⇒ WalletProposeRq(None, Some(makePassword()), Some("ed25519")))
    auto.toList ::: uuid.toList
  }


  def executeFixture(fixs: List[WalletProposeRq]) = {
    val jsons = fixs.map(_.asJsonObject)
    val ans: Either[AppError, List[String]] = jsons.traverse(doCall).map(v ⇒ v.map(reqres2string))
    val each                                = ans.right.value
    val jsonArray: String                   = each.mkString("[", ",\n", "\n]\n")

    // Quick Hack to screen cut past
    logger.debug("JSON ARRAY\n" + jsonArray)

  }

  test("secp password fixture generator") {
    executeFixture(secpPasswordFixture)
  }

  test("ed password fixture generator"){
    executeFixture(ed25519PasswordFixture)
  }

  test("Genesis Propose") {

  }

}
