package com.paytm.wallet.service;

import com.paytm.wallet.constants.ErrorCode;
import com.paytm.wallet.dto.WalletResponse;
import com.paytm.wallet.entity.Wallet;
import com.paytm.wallet.exception.NotFoundException;
import com.paytm.wallet.mapper.WalletMapper;
import com.paytm.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;

    @Override
    @Transactional
    public WalletResponse getOrCreate(String userId) {
        int inserted = walletRepository.insertIfAbsent(userId);
        Wallet wallet = walletRepository.findByUserId(userId)
                // Cannot happen: the insert-or-existing row is committed/visible here.
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet missing after insertIfAbsent for user " + userId));
        if (inserted == 1) {
            log.info("wallet.created wallet_id={} user_id={}", wallet.getId(), userId);
        }
        return walletMapper.toResponse(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getById(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.WALLET_NOT_FOUND, "Wallet " + walletId + " not found"));
        return walletMapper.toResponse(wallet);
    }
}
