package com.paytm.wallet.constants;

/**
 * Terminal outcome of a transfer. A transfer row is written exactly once, in the
 * same transaction as the money movement, so its status is immutable after commit.
 */
public enum TransferStatus {
    /**
     * Transient in-flight state used only to claim the idempotency key at the start of the
     * money-movement transaction. It is always overwritten with a terminal status before
     * commit, so a PENDING row is never observable by another transaction and never
     * persists (a crash rolls the whole transaction back).
     */
    PENDING,
    /** Debit + credit applied. */
    SUCCEEDED,
    /** Rejected cleanly (e.g. insufficient funds). No money moved. */
    DECLINED
}
