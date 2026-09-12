package com.paytm.wallet.controller;

import com.paytm.wallet.dto.WalletResponse;
import com.paytm.wallet.service.WalletService;
import com.paytm.wallet.util.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * Get-or-create the caller's wallet. Idempotent, so returns 200 rather than 201: the
     * user is identified by the bearer token, and a repeat call returns the same wallet.
     */
    @PostMapping
    public ResponseEntity<WalletResponse> getOrCreate() {
        return ResponseEntity.ok(walletService.getOrCreate(CurrentUserContext.get()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(walletService.getById(id));
    }
}
