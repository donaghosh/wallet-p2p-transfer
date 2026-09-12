package com.paytm.wallet.service;

import com.paytm.wallet.dto.WalletResponse;

public interface WalletService {

    /** Get the caller's wallet, creating a zero-balance one if absent. Race-free. */
    WalletResponse getOrCreate(String userId);

    /** Fetch a wallet by id, or throw {@code NotFoundException}. */
    WalletResponse getById(Long walletId);
}
