package com.paytm.wallet.dto;

import com.paytm.wallet.constants.TransferStatus;
import java.time.Instant;

/**
 * Transfer view returned by the API. A declined transfer is a normal, recorded outcome
 * ({@code status=DECLINED}, {@code decline_reason} populated), not an HTTP error.
 */
public record TransferResponse(
        Long transferId,
        String idempotencyKey,
        Long from,
        Long to,
        long amountPaise,
        TransferStatus status,
        String declineReason,
        Instant createdAt) {
}
