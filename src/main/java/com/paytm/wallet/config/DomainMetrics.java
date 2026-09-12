package com.paytm.wallet.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Domain counters exposed at {@code /actuator/prometheus} alongside the built-in HTTP
 * request-rate / latency / error metrics. Low-cardinality names, no per-user tags.
 */
@Component
public class DomainMetrics {

    private final Counter transfersCreated;
    private final Counter transfersDeclinedInsufficientFunds;
    private final Counter idempotentReplays;

    public DomainMetrics(MeterRegistry registry) {
        this.transfersCreated = Counter.builder("wallet.transfers.created")
                .description("Transfers newly created and succeeded")
                .register(registry);
        this.transfersDeclinedInsufficientFunds = Counter.builder("wallet.transfers.declined")
                .description("Transfers declined for insufficient funds")
                .tag("reason", "insufficient_funds")
                .register(registry);
        this.idempotentReplays = Counter.builder("wallet.transfers.idempotent_replay")
                .description("Idempotent replays served from an existing transfer")
                .register(registry);
    }

    public void transferCreated() {
        transfersCreated.increment();
    }

    public void transferDeclinedInsufficientFunds() {
        transfersDeclinedInsufficientFunds.increment();
    }

    public void idempotentReplay() {
        idempotentReplays.increment();
    }
}
