package com.odenzo.ripple.fixturesgen

import java.security.SecureRandom
import java.util.UUID
import scala.collection.immutable

import io.circe.{Decoder, Json}
import io.circe.syntax._
import org.scalatest.FunSuite
import org.scalatest.concurrent.PatienceConfiguration

import com.odenzo.ripple.fixturesgen.ScenerioBuilder.{advanceLedger, doCall, doTxnCall, getOrLog}
import com.odenzo.ripple.integration_testkit.RequestResponse
import com.odenzo.ripple.integration_tests.integration_testkit.IntegrationTestFixture
import com.odenzo.ripple.models.atoms.{AccountAddr, AccountKeys, Base58, Drops, RippleKeyType, RippleSeed}
import com.odenzo.ripple.models.wireprotocol.accountinfo.WalletProposeRq
import com.odenzo.ripple.models.wireprotocol.transactions.{SignRq, SignRs, SubmitRq, SubmitRs}
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.{CommonTx, PaymentTx}
import com.odenzo.ripple.utils.CirceUtils
import com.odenzo.ripple.utils.caterrors.AppError
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr

/** This is to make some JSON data with transactions and the submit and sign stuff.
  * Goal is to use to test transaction hash which is input to signing and also signing.
  * */
class SigningFixtureMakerTest extends FunSuite with FixtureGeneratorUtils {

  import cats.implicits._

  val genesis = ScenerioBuilder.genesis
  val wallets = ScenerioBuilder.wallets.drop(1)

  val secureRND = SecureRandom.getInstanceStrong

  def makePassword(): String = {
    secureRND.generateSeed(4) // 32 bit int
    UUID.randomUUID().toString
  }

  val commonTx: CommonTx = CommonTx()

  def makeTransfer = {}

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
    val secpWallet: AccountKeys = wallets.find(_.key_type === RippleKeyType("secp256k1")).head
    val otherWallets            = wallets.filterNot(secpWallet === _)

    /** In this case no need to submit, other than to check all ok */
    def doSimple(sender: AccountKeys, recv: AccountKeys, amt: Drops): RequestResponse[Json, Json] = {
      val rq                                  = ScenerioBuilder.createXrpTransfer(sender, recv, amt)
      val signRq                              = SignRq(rq.asJson, sender.secret)
      val signed: RequestResponse[Json, Json] = getOrLog(doCall(signRq.asJson))
      val signRs: SignRs = getOrLog(decodeTxnCall(signed, Decoder[SignRs]))

      
      val subRq     = SubmitRq(signRs.tx_blob).asJson
      val submitted = getOrLog(doTxnCall(subRq, Decoder[SubmitRs]))
      advanceLedger()
      signed
    }

    val res: immutable.Seq[RequestResponse[Json, Json]] =
      (555 to 600).map {v ⇒ doSimple(secpWallet, otherWallets.head, Drops.fromXrp(v)) }

    logAnswerToFile("logs/secpwallets.json", ScenerioBuilder.walletJson)
    logAnswerToFile("logs/secptxn.json",res.toList)

  }

  test("ed password fixture generator") {
    //executeFixture(ed25519PasswordFixture)
  }

}
