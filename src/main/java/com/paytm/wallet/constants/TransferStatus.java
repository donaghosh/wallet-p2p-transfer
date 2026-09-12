package com.paytm.wallet.constants;

/**
 * Terminal outcome of a transfer. A transfer row is written exactly once, in the
 * same transaction as the money movement, so its status is immutable after commit.
 */
public enum TransferStatus {
    /** Debit + credit applied. */
    SUCCEEDED,
    /** Rejected cleanly (e.g. insufficient funds). No money moved. */
    DECLINED
}
