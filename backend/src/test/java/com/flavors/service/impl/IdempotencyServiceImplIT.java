package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.TestcontainersConfiguration;
import com.flavors.exception.IdempotencyConflictException;
import com.flavors.service.IdempotencyService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** NF-6: a retried request runs once. Each test uses its own random key. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class IdempotencyServiceImplIT {

    record Receipt(String id, long amountCents) {
    }

    @Autowired
    IdempotencyService service;

    @Autowired
    JdbcTemplate jdbc;

    String key = UUID.randomUUID().toString();

    @Test
    void repeatReturnsStoredResultWithoutRunningAgain() {
        AtomicInteger runs = new AtomicInteger();

        Receipt first = run("pay", "amount=500", runs);
        Receipt second = run("pay", "amount=500", runs);

        assertThat(runs).hasValue(1);
        assertThat(second).isEqualTo(first);
        assertThat(rowStatus("pay")).isEqualTo("COMPLETED");
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        AtomicInteger runs = new AtomicInteger();
        run("pay", "amount=500", runs);

        assertThatThrownBy(() -> run("pay", "amount=900", runs))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(runs).hasValue(1);
    }

    @Test
    void sameKeyInAnotherScopeIsIndependent() {
        AtomicInteger runs = new AtomicInteger();

        run("pay", "amount=500", runs);
        run("webhook:stripe", "amount=500", runs);

        assertThat(runs).hasValue(2);
    }

    @Test
    void failedActionLeavesNoKeyAndCanBeRetried() {
        assertThatThrownBy(() -> service.execute("pay", key, "amount=500", () -> {
            throw new IllegalStateException("provider down");
        }, Receipt.class)).isInstanceOf(IllegalStateException.class);
        assertThat(rowStatus("pay")).isNull();

        AtomicInteger runs = new AtomicInteger();
        run("pay", "amount=500", runs);

        assertThat(runs).hasValue(1);
        assertThat(rowStatus("pay")).isEqualTo("COMPLETED");
    }

    @Test
    void concurrentRetriesRunTheActionOnce() throws Exception {
        int threads = 8;
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Receipt>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Receipt> call = () -> {
                    start.await(); // release all threads together to maximise the race
                    return service.execute("pay", key, "amount=500", () -> {
                        sleep(200); // keep the first request open while the others arrive
                        return new Receipt("r-" + runs.incrementAndGet(), 500);
                    }, Receipt.class);
                };
                results.add(pool.submit(call));
            }
            start.countDown();

            List<Receipt> receipts = new ArrayList<>();
            for (Future<Receipt> f : results) {
                receipts.add(f.get());
            }
            assertThat(runs).hasValue(1);
            assertThat(receipts).containsOnly(new Receipt("r-1", 500));
        } finally {
            pool.shutdownNow();
        }
    }

    private Receipt run(String scope, String fingerprint, AtomicInteger runs) {
        return service.execute(scope, key, fingerprint,
                () -> new Receipt("r-" + runs.incrementAndGet(), 500), Receipt.class);
    }

    private String rowStatus(String scope) {
        List<String> rows = jdbc.queryForList(
                "SELECT status FROM idempotency_key WHERE scope = ? AND key = ?", String.class, scope, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
