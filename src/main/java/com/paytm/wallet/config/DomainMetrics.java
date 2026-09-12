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

    private final Counter transfersSucceeded;
    private final Counter transfersDeclinedInsufficientFunds;
    private final Counter idempotentReplays;

    public DomainMetrics(MeterRegistry registry) {
        // Named "succeeded" rather than "created": a Prometheus counter name ending in
        // "created" collides with the reserved _created (creation-timestamp) suffix and is
        // dropped from exposition. This counter tracks newly created + succeeded transfers.
        this.transfersSucceeded = Counter.builder("wallet.transfers.succeeded")
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

    public void transferSucceeded() {
        transfersSucceeded.increment();
    }

    public void transferDeclinedInsufficientFunds() {
        transfersDeclinedInsufficientFunds.increment();
    }

    public void idempotentReplay() {
        idempotentReplays.increment();
    }
}
