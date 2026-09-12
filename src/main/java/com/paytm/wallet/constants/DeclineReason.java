package com.paytm.wallet.constants;

/**
 * Why a transfer ended {@link TransferStatus#DECLINED}. Persisted on the transfer row
 * and surfaced in the response and logs.
 */
public enum DeclineReason {
    INSUFFICIENT_FUNDS
}
