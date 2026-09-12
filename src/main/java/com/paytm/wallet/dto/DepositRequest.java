package com.paytm.wallet.dto;

import jakarta.validation.constraints.Positive;

/**
 * Funding request. {@code amount_paise} integer paise, positive. A deposit is a mint that
 * increases the system total — it funds wallets for use and sits intentionally outside the
 * transfer-conservation invariant (which only constrains money moved between wallets).
 */
public record DepositRequest(@Positive(message = "amount_paise must be positive") long amountPaise) {
}
