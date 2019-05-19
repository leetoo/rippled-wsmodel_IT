package com.odenzo.ripple.CommonFunctions

import com.odenzo.ripple.integration_testkit.{TestCallResults, TestCommandHarness, WebSocketJsonConnection}
import com.odenzo.ripple.models.atoms.{AccountAddr, TxnSequence}
import com.odenzo.ripple.models.support.{Codec, RippleAccount}
import com.odenzo.ripple.models.wireprotocol.accountinfo.{AccountInfoRq, AccountInfoRs}
import com.odenzo.ripple.localops.utils.caterrors.AppError
import com.odenzo.ripple.localops.utils.caterrors.CatsTransformers.ErrorOr
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

/** For transactions we need the seq field populated */
object GetNextAccountSequence  {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val con: ErrorOr[WebSocketJsonConnection] = SharedRippleConnection.conn

  /** Gets the sequence for the account from last validated ledger */
  def seqForAccount(account: AccountAddr): Either[AppError, TxnSequence] = {

    val codec: Codec[AccountInfoRq, AccountInfoRs] = new Codec(AccountInfoRq.encoder, AccountInfoRs.decoder)

    val rq                                         = AccountInfoRq(account, queue = false, signer_lists = false, strict = true)
    con.flatMap { c: WebSocketJsonConnection =>
      val rs: TestCallResults[AccountInfoRq, AccountInfoRs] = TestCommandHarness.doCommand(codec, c, rq)
      val obj: ErrorOr[AccountInfoRs]                       = rs.result
      obj.map(_.account_data.sequence)
    }
  }
}
