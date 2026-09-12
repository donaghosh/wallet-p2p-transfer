package com.paytm.wallet.dto;

/**
 * Wallet view returned by the API. Serialized snake_case: {@code wallet_id}, {@code
 * user_id}, {@code balance_paise}.
 */
public record WalletResponse(Long walletId, String userId, long balancePaise) {
}
