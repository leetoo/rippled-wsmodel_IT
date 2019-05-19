package com.odenzo.ripple.fixturesgen

import cats._
import cats.data._
import cats.implicits._
import java.security.SecureRandom
import java.util.UUID

import com.odenzo.ripple.fixturesgen.ScenerioBuilder.advanceLedger
import com.odenzo.ripple.integration_testkit.RequestResponse
import com.odenzo.ripple.models.atoms.{AccountKeys, Drops, RippleKeyType, TxnSequence}
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.{CommonTx, PaymentTx}
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRq, SignRs, SubmitRq, SubmitRs}
import com.odenzo.ripple.localops.utils.caterrors.AppError
import io.circe.syntax._
import io.circe.{Decoder, Json}
import org.scalatest.FunSuite

import scala.collection.immutable

/** This is to make some JSON data with transactions and the submit and sign stuff.
  * Goal is to use to test transaction hash which is input to signing and also signing.
  * */
class SigningFixtureMakerTest extends FunSuite with FixtureGeneratorUtils {

  import cats.implicits._

  val genesis: Either[AppError, AccountKeys] = ScenerioBuilder.genesis
 val allWallets: Either[AppError, List[AccountKeys]] = ScenerioBuilder.wallets
  val wallets: Either[AppError, List[AccountKeys]] = allWallets.fmap(v=> v.drop(1))

  val secureRND: SecureRandom = SecureRandom.getInstanceStrong

  def makePassword(): String = {
    secureRND.generateSeed(4) // 32 bit int
    UUID.randomUUID().toString
  }



  def makeSignRq(txJson: Json, key: AccountKeys): SignRq = {
    val sig = key.master_key

    SignRq(txJson, sig)
  }

//  def secpFixture: List[SignRq] = {
//    val req: SignRq = makeSignRq(makePaymentTx(sepKey).asJson, sepKey)
//    //List(req)
//  }

  def executeFixture(fixs: List[SignRq]) = {
    val jsons                               = fixs.map(_.asJson)
    val ans: Either[AppError, List[String]] = jsons.traverse(doCall).map(v ⇒ v.map(reqres2string))
    val each                                = ans.right.value
    val jsonArray: String                   = each.mkString("[", ",\n", "\n]\n")

    // Quick Hack to screen cut past
    logger.debug("JSON ARRAY\n" + jsonArray)

  }

  test("Init") {
    genesis shouldEqual genesis
  }
  test("secp fixture generator") {
    val accounts = getOrLog(wallets)

    val secpWallet: AccountKeys = accounts.find(_.key_type === RippleKeyType("secp256k1")).get
    val otherWallets            = accounts.find(secpWallet != _)

    /** In this case no need to submit, other than to check all ok */
    def doSimple(sender: AccountKeys, recv: AccountKeys, amt: Drops): RequestResponse[Json, Json] = {
      val seq: TxnSequence = getOrLog(ScenerioBuilder.getAccountSequence(sender.address))
      val rq: PaymentTx = ScenerioBuilder.createXrpTransfer(sender, recv, amt,seq)
      val signRq                              = SignRq(rq.asJson, sender.secret)
      val signed: RequestResponse[Json, Json] = getOrLog(doCall(signRq.asJson))
      val signRs: SignRs = getOrLog(decodeTxnCall(signed, Decoder[SignRs]))

      
      val subRq     = SubmitRq(signRs.tx_blob).asJson
      val submitted = getOrLog(doTxnCall(subRq, Decoder[SubmitRs]))
      advanceLedger()
      signed
    }

    val res: immutable.Seq[RequestResponse[Json, Json]] =
      (555L to 567).map {v ⇒ doSimple(secpWallet, otherWallets.head, Drops.fromXrp(v)) }

    logAnswerToFile("logs/secpwallets.json", getOrLog(ScenerioBuilder.walletJson))
    logAnswerToFile("logs/secptxn.json",res.toList)

  }

  test("ed password fixture generator") {
    //executeFixture(ed25519PasswordFixture)
  }

}
