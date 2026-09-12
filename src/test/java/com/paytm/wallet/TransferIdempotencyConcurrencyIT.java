package com.paytm.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paytm.wallet.constants.TransferStatus;
import com.paytm.wallet.dto.CreateTransferRequest;
import com.paytm.wallet.dto.TransferResponse;
import com.paytm.wallet.dto.WalletResponse;
import com.paytm.wallet.exception.ConflictException;
import com.paytm.wallet.repository.WalletRepository;
import com.paytm.wallet.service.TransferService;
import com.paytm.wallet.service.WalletService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Gate 2 — idempotent exactly-once transfer: the same key fired K times concurrently must
 * produce exactly one debit/credit and identical responses; a same-key/different-body
 * replay must be a 409.
 */
class TransferIdempotencyConcurrencyIT extends AbstractIntegrationTest {

    private static final int CONCURRENCY = 30;
    private static final long INITIAL = 1_000_000L;
    private static final long AMOUNT = 250L;

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    void sameKeyFiredConcurrentlyAppliesExactlyOnce() throws Exception {
        long from = newFundedWallet();
        long to = newFundedWallet();
        String key = "idem-" + UUID.randomUUID();
        CreateTransferRequest request = new CreateTransferRequest(from, to, AMOUNT, key);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch startGate = new CountDownLatch(1);
        List<TransferResponse> responses = new CopyOnWriteArrayList<>();

        List<Callable<TransferResponse>> tasks = IntStream.range(0, CONCURRENCY)
                .<Callable<TransferResponse>>mapToObj(i -> () -> {
                    startGate.await();
                    TransferResponse r = transferService.create(request);
                    responses.add(r);
                    return r;
                })
                .toList();
        List<Future<TransferResponse>> futures = tasks.stream().map(pool::submit).toList();
        startGate.countDown();
        for (Future<TransferResponse> f : futures) {
            f.get();
        }
        pool.shutdown();

        // Exactly one debit and one credit total, despite K concurrent submissions.
        assertThat(balance(from)).isEqualTo(INITIAL - AMOUNT);
        assertThat(balance(to)).isEqualTo(INITIAL + AMOUNT);

        // All responses identical: same transfer id and SUCCEEDED status.
        Set<Long> ids = responses.stream().map(TransferResponse::transferId).collect(Collectors.toSet());
        assertThat(responses).hasSize(CONCURRENCY);
        assertThat(ids).hasSize(1);
        assertThat(responses).allSatisfy(r -> assertThat(r.status()).isEqualTo(TransferStatus.SUCCEEDED));
    }

    @Test
    void sameKeyDifferentBodyIsConflict() {
        long from = newFundedWallet();
        long to = newFundedWallet();
        String key = "idem-" + UUID.randomUUID();

        transferService.create(new CreateTransferRequest(from, to, AMOUNT, key));

        assertThatThrownBy(() ->
                transferService.create(new CreateTransferRequest(from, to, AMOUNT + 1, key)))
                .isInstanceOf(ConflictException.class);
    }

    private long newFundedWallet() {
        WalletResponse w = walletService.getOrCreate("user-" + UUID.randomUUID());
        walletService.deposit(w.walletId(), INITIAL);
        return w.walletId();
    }

    private long balance(long walletId) {
        return walletRepository.findById(walletId).orElseThrow().getBalancePaise();
    }
}
