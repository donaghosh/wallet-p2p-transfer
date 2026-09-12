package com.paytm.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.paytm.wallet.dto.WalletResponse;
import com.paytm.wallet.repository.WalletRepository;
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
 * Gate 1 — race-free get-or-create: N concurrent get-or-create calls for one fresh user
 * must yield exactly one wallet, and every caller must observe the same wallet id.
 */
class WalletGetOrCreateConcurrencyIT extends AbstractIntegrationTest {

    private static final int CONCURRENCY = 50;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    void concurrentGetOrCreateYieldsExactlyOneWallet() throws Exception {
        String userId = "user-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Long> observedWalletIds = new CopyOnWriteArrayList<>();

        List<Callable<WalletResponse>> tasks = IntStream.range(0, CONCURRENCY)
                .<Callable<WalletResponse>>mapToObj(i -> () -> {
                    startGate.await();
                    WalletResponse w = walletService.getOrCreate(userId);
                    observedWalletIds.add(w.walletId());
                    return w;
                })
                .toList();

        List<Future<WalletResponse>> futures = tasks.stream().map(pool::submit).toList();
        startGate.countDown(); // release all threads at once for maximum contention
        for (Future<WalletResponse> f : futures) {
            f.get();
        }
        pool.shutdown();

        // Exactly one wallet row exists for this user.
        long rowsForUser = walletRepository.findAll().stream()
                .filter(w -> userId.equals(w.getUserId()))
                .count();
        assertThat(rowsForUser).isEqualTo(1);

        // Every concurrent caller observed the same single wallet id.
        Set<Long> distinctIds = observedWalletIds.stream().collect(Collectors.toSet());
        assertThat(observedWalletIds).hasSize(CONCURRENCY);
        assertThat(distinctIds).hasSize(1);
    }
}
