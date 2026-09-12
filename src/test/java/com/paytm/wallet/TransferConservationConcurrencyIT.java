package com.paytm.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.paytm.wallet.dto.CreateTransferRequest;
import com.paytm.wallet.dto.WalletResponse;
import com.paytm.wallet.repository.WalletRepository;
import com.paytm.wallet.service.TransferService;
import com.paytm.wallet.service.WalletService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Gate 3 — conservation + no-overdraft under contention: hundreds of concurrent transfers
 * among a small set of wallets (including A&rarr;B and B&rarr;A simultaneously, and some
 * that overdraw) must leave the total unchanged, no balance negative, and must not deadlock
 * or 500.
 */
class TransferConservationConcurrencyIT extends AbstractIntegrationTest {

    private static final int WALLETS = 5;
    private static final long INITIAL = 100_000L;
    private static final int TRANSFERS = 500;

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    void concurrentTransfersConserveTotalAndNeverOverdraw() throws Exception {
        List<Long> wallets = new ArrayList<>();
        for (int i = 0; i < WALLETS; i++) {
            WalletResponse w = walletService.getOrCreate("user-" + UUID.randomUUID());
            walletService.deposit(w.walletId(), INITIAL);
            wallets.add(w.walletId());
        }
        long totalBefore = totalBalance(wallets);
        assertThat(totalBefore).isEqualTo(WALLETS * INITIAL);

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < TRANSFERS; i++) {
            tasks.add(() -> {
                startGate.await();
                try {
                    int a = ThreadLocalRandom.current().nextInt(WALLETS);
                    int b = ThreadLocalRandom.current().nextInt(WALLETS);
                    while (b == a) {
                        b = ThreadLocalRandom.current().nextInt(WALLETS);
                    }
                    long amount = ThreadLocalRandom.current().nextLong(1, 30_000);
                    // A declined transfer is returned (not thrown); anything thrown here is a bug.
                    transferService.create(new CreateTransferRequest(
                            wallets.get(a), wallets.get(b), amount, "t-" + UUID.randomUUID()));
                } catch (Throwable t) {
                    unexpected.add(t);
                }
                return null;
            });
        }
        List<Future<Void>> futures = tasks.stream().map(pool::submit).toList();
        startGate.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(unexpected).as("no deadlocks / 500s under contention").isEmpty();
        assertThat(totalBalance(wallets)).as("conservation").isEqualTo(totalBefore);
        for (Long id : wallets) {
            assertThat(walletRepository.findById(id).orElseThrow().getBalancePaise())
                    .as("no negative balance for wallet %s", id)
                    .isGreaterThanOrEqualTo(0L);
        }
    }

    private long totalBalance(List<Long> wallets) {
        return wallets.stream()
                .mapToLong(id -> walletRepository.findById(id).orElseThrow().getBalancePaise())
                .sum();
    }
}
