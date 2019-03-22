package com.odenzo.ripple.integration_testkit

import com.odenzo.ripple.models.atoms.{LedgerIndex, RippleTime}
import com.odenzo.ripple.models.support.RippleGenericResponse
import com.odenzo.ripple.utils.caterrors.CatsTransformers.ErrorOr

/** Sent as an event whenever we know the validated ledger has been incremented/changed */
case class ValidatedLedgerChangedEv(ledger: LedgerIndex, time: RippleTime)

/**
  * Short version of LedgerClosedMsg
  * @param ledger_index  The XRPL index number of the closed ledger
  * @param ledger_time When the ledger even happened (i.e. when closed)
  */
case class LedgerInfo(ledger_index: LedgerIndex, ledger_time: RippleTime)

/**
  * Status attribute saying the state of trying to ensure that a transaction has been validated/finalized
  * in a consensus ledger.
  * FIXME: Convert to enumeratum
  */
trait LedgerValidationStatus
case object TxnValidated         extends LedgerValidationStatus
case object TxnValidationPending extends LedgerValidationStatus
case object TxnValidationExpired extends LedgerValidationStatus
case object TxnValidatedFailed   extends LedgerValidationStatus

// Hack to get easy case class matching.
case class LedgerValidationRs(v: ErrorOr[LedgerValidationResult])

/** In flux. Currenly this gives the result of a tx inquiry response (?) in raw. */
case class LedgerValidationResult(raw: RippleGenericResponse, status: LedgerValidationStatus) {
  def isPending: Boolean = status == TxnValidationPending
}
