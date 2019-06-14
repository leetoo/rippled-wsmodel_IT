package com.odenzo.ripple.integration_testkit

import monocle.PLens

import com.odenzo.ripple.models.atoms.Memos
import com.odenzo.ripple.models.wireprotocol.transactions.transactiontypes.{CommonTx, PaymentTx, RippleTransaction}

trait MsgLenses {

  import monocle.Lens
  import monocle.macros.GenLens


  val lensTxMemo = PaymentTx.lensCommonTx composeLens CommonTx.lensMemos
}

object MsgLenses extends MsgLenses
