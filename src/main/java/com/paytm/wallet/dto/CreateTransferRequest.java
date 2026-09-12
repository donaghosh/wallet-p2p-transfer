package com.paytm.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Transfer request body. Serialized snake_case: {@code from}, {@code to},
 * {@code amount_paise}, {@code idempotency_key}. Amount is integer paise.
 */
public record CreateTransferRequest(
        @NotNull(message = "from is required") Long from,
        @NotNull(message = "to is required") Long to,
        @Positive(message = "amount_paise must be positive") long amountPaise,
        @NotBlank(message = "idempotency_key is required")
        @Size(max = 128, message = "idempotency_key must be at most 128 chars")
        String idempotencyKey) {
}
