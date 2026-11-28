package com.swiftdrop.webhook;

import com.swiftdrop.webhook.delivery.AttemptOutcome;
import com.swiftdrop.webhook.delivery.ClaimedDelivery;
import com.swiftdrop.webhook.delivery.DeliveryAttemptRecorder;
import com.swiftdrop.webhook.delivery.DeliveryProperties;
import com.swiftdrop.webhook.delivery.DeliveryRetryPolicy;
import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.webhook.domain.repository.DeliveryJobRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lease, fence, release and claim behaviour of the delivery tables against real PostgreSQL: the SQL in
 * {@link DeliveryJobRepository} and the recorder that drives it. Time comes from the database ({@code now()}), so
 * expiry and due times are set relative to it in SQL instead of waiting.
 */
@Testcontainers
class DeliveryLeaseFencingIT {

    private static final int MAX_ATTEMPTS = 4;
    private static final Duration LEASE = Duration.ofSeconds(60);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static DeliveryJobRepository jobs;
    private static DeliveryAttemptRecorder recorder;
    private static TransactionTemplate tx;

    private UUID merchant;
    private UUID endpoint;

    @BeforeAll
    static void migrate() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        jobs = new DeliveryJobRepository(new NamedParameterJdbcTemplate(dataSource));
        // The approved schedule (D1) without jitter, so stored delays are exact.
        var properties = new DeliveryProperties(List.of(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10)),
                0.0, Duration.ofMinutes(10), 4, 1, 10, Duration.ofSeconds(2), LEASE);
        recorder = new DeliveryAttemptRecorder(jobs, new DeliveryRetryPolicy(properties), properties);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE webhook_delivery_attempts, webhook_deliveries, order_merchant_map, inbound_events, webhook_endpoints CASCADE");
        merchant = UUID.randomUUID();
        endpoint = endpoint(merchant);
    }

    // ---- fixtures --------------------------------------------------------------------------------------------------

    private UUID endpoint(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO webhook_endpoints(endpoint_id, merchant_id, target_url, secret_key) VALUES (?,?,?,?)",
                id, owner, "https://shop.example.com/hook", "whsec_x");
        return id;
    }

    /** A due PENDING job for one event of {@code order}, which occurred {@code secondsAgo} seconds ago. */
    private UUID job(UUID endpointId, UUID order, int secondsAgo) {
        UUID event = UUID.randomUUID(), delivery = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inbound_events(event_id, event_type, schema_version, correlation_id, order_id, payload,
                    occurred_at, processing_status, merchant_id, resolved_at)
                VALUES (?, 'parcel.status.changed', '1', ?, ?, '{}'::jsonb, now() - make_interval(secs => ?), 'RESOLVED', ?, now())
                """, event, UUID.randomUUID(), order, secondsAgo, merchant);
        jdbc.update("""
                INSERT INTO webhook_deliveries(delivery_id, inbound_event_id, endpoint_id, merchant_id, status, attempt_count, next_attempt_at)
                VALUES (?, ?, ?, ?, 'PENDING', 0, now() - interval '1 second')
                """, delivery, event, endpointId, merchant);
        return delivery;
    }

    private UUID job() {
        return job(endpoint, UUID.randomUUID(), 0);
    }

    /** Claims every due job for {@code owner}, keyed by delivery id. */
    private Map<UUID, ClaimedDelivery> claimAll(String owner) {
        Map<UUID, ClaimedDelivery> byId = new java.util.HashMap<>();
        recorder.claim(owner, 100).forEach(c -> byId.put(c.deliveryId(), c));
        return byId;
    }

    /** Claims due jobs for {@code owner} and returns the claim of {@code delivery}, which must be among them. */
    private ClaimedDelivery claimOnly(UUID delivery, String owner) {
        List<ClaimedDelivery> claimed = recorder.claim(owner, 100);
        assertThat(claimed).extracting(ClaimedDelivery::deliveryId).contains(delivery);
        return claimed.stream().filter(c -> c.deliveryId().equals(delivery)).findFirst().orElseThrow();
    }

    private void expireLease(UUID delivery) {
        jdbc.update("UPDATE webhook_deliveries SET lease_expires_at = now() - interval '1 second' WHERE delivery_id = ?", delivery);
    }

    private void makeDue(UUID delivery) {
        jdbc.update("UPDATE webhook_deliveries SET next_attempt_at = now() - interval '1 second' WHERE delivery_id = ?", delivery);
    }

    private Map<String, Object> row(UUID delivery) {
        return jdbc.queryForMap("""
                SELECT status, attempt_count, lease_owner, lease_expires_at, next_attempt_at, version, updated_at,
                       EXTRACT(EPOCH FROM (next_attempt_at - updated_at)) AS delay_seconds
                FROM webhook_deliveries WHERE delivery_id = ?
                """, delivery);
    }

    private List<Map<String, Object>> attempts(UUID delivery) {
        return jdbc.queryForList("SELECT attempt_number, http_status_code, error_class, latency_ms, is_success "
                + "FROM webhook_delivery_attempts WHERE delivery_id = ? ORDER BY attempt_number", delivery);
    }

    private static AttemptOutcome ok() {
        return new AttemptOutcome(200, null, false, 12, null);
    }

    private static AttemptOutcome serverError() {
        return new AttemptOutcome(500, AttemptErrorClass.HTTP_SERVER_ERROR, true, 12, null);
    }

    private static double seconds(Object value) {
        return ((Number) value).doubleValue();
    }

    // ---- A. expired lease recovery ----------------------------------------------------------------------------------

    @Test
    @DisplayName("A: an expired lease is closed as UNKNOWN and rescheduled on the normal retry delay")
    void expiredLeaseIsRecoveredAndRescheduled() {
        UUID delivery = job();
        claimOnly(delivery, "w1");
        expireLease(delivery);

        assertThat(recorder.recoverExpiredLeases(100)).isEqualTo(1);

        Map<String, Object> after = row(delivery);
        assertThat(after.get("status")).isEqualTo("RETRY");
        assertThat(after.get("attempt_count")).isEqualTo(1);
        assertThat(after.get("lease_owner")).isNull();
        assertThat(after.get("lease_expires_at")).isNull();
        assertThat(seconds(after.get("delay_seconds"))).isEqualTo(30.0);
        assertThat(attempts(delivery)).singleElement().satisfies(a -> {
            assertThat(a.get("attempt_number")).isEqualTo(1);
            assertThat(a.get("error_class")).isEqualTo("UNKNOWN");
            assertThat(a.get("http_status_code")).isNull();
            assertThat(a.get("latency_ms")).isNull();
            assertThat(a.get("is_success")).isEqualTo(false);
        });
    }

    @Test
    @DisplayName("A: a lease that expires on the last attempt ends the delivery DEAD")
    void expiredLeaseOnTheLastAttemptIsDead() {
        UUID delivery = job();
        jdbc.update("UPDATE webhook_deliveries SET status = 'RETRY', attempt_count = ? WHERE delivery_id = ?", MAX_ATTEMPTS - 1, delivery);
        assertThat(claimOnly(delivery, "w1").attemptNumber()).isEqualTo(MAX_ATTEMPTS);
        expireLease(delivery);

        assertThat(recorder.recoverExpiredLeases(100)).isEqualTo(1);

        Map<String, Object> after = row(delivery);
        assertThat(after.get("status")).isEqualTo("DEAD");
        assertThat(after.get("next_attempt_at")).isNull();
        assertThat(recorder.claim("w2", 100)).isEmpty();
    }

    @Test
    @DisplayName("A: a lease that has not expired is left alone, and recovery is idempotent")
    void validLeasesAreUntouchedAndRecoveryIsIdempotent() {
        UUID held = job();
        UUID lost = job();
        Map<UUID, ClaimedDelivery> claims = claimAll("w1");
        ClaimedDelivery heldClaim = claims.get(held);
        jdbc.update("UPDATE webhook_deliveries SET lease_expires_at = now() + interval '1 second' WHERE delivery_id = ?", held);
        expireLease(lost);
        Map<String, Object> heldBefore = row(held);

        assertThat(recorder.recoverExpiredLeases(100)).isEqualTo(1);
        assertThat(recorder.recoverExpiredLeases(100)).isZero();
        assertThat(recorder.recoverExpiredLeases(100)).isZero();

        assertThat(row(held)).isEqualTo(heldBefore);
        assertThat(attempts(held)).isEmpty();
        assertThat(attempts(lost)).hasSize(1);
        // The worker holding the valid lease can still record its result.
        assertThat(recorder.record(heldClaim, ok(), Instant.now()).applied()).isTrue();
        assertThat(row(held).get("status")).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("A: finished and waiting deliveries are never treated as expired")
    void onlyInFlightRowsAreRecovered() {
        UUID succeeded = job();
        UUID dead = job();
        Map<UUID, ClaimedDelivery> claims = claimAll("w1");
        recorder.record(claims.get(succeeded), ok(), Instant.now());
        recorder.record(claims.get(dead), new AttemptOutcome(400, AttemptErrorClass.HTTP_CLIENT_ERROR, false, 5, null),
                Instant.now());
        UUID pending = job();
        Map<String, Object> succeededBefore = row(succeeded), deadBefore = row(dead), pendingBefore = row(pending);

        assertThat(recorder.recoverExpiredLeases(100)).isZero();

        assertThat(row(succeeded)).isEqualTo(succeededBefore);
        assertThat(row(dead)).isEqualTo(deadBefore);
        assertThat(row(pending)).isEqualTo(pendingBefore);
    }

    @Test
    @DisplayName("A: two recovery runs never close the same expired lease twice (SKIP LOCKED)")
    void concurrentRecoveryRunsSkipEachOthersRows() throws Exception {
        UUID delivery = job();
        claimOnly(delivery, "w1");
        expireLease(delivery);
        CountDownLatch locked = new CountDownLatch(1), finish = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // Replica 1 holds the expired row locked inside its recovery transaction.
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
                int seen = jobs.lockExpiredLeases(100).size();
                locked.countDown();
                await(finish);
                return seen;
            }), pool);
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            // Replica 2 runs recovery meanwhile: the locked row is skipped, nothing is written.
            Integer recoveredMeanwhile = tx.execute(status -> recorder.recoverExpiredLeases(100));
            assertThat(recoveredMeanwhile).isZero();
            assertThat(attempts(delivery)).isEmpty();

            finish.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            finish.countDown();
            pool.shutdownNow();
        }
        // Replica 1 only locked the row and wrote nothing; a later run recovers it exactly once.
        assertThat(recorder.recoverExpiredLeases(100)).isEqualTo(1);
        assertThat(attempts(delivery)).hasSize(1);
    }

    // ---- B. stale-result fencing ------------------------------------------------------------------------------------

    @Test
    @DisplayName("B: after recovery and a re-claim by another worker, the old worker's success changes nothing")
    void staleSuccessCannotOverwriteANewerClaim() {
        UUID delivery = job();
        ClaimedDelivery w1 = claimOnly(delivery, "w1");
        expireLease(delivery);
        recorder.recoverExpiredLeases(100);
        makeDue(delivery);
        ClaimedDelivery w2 = claimOnly(delivery, "w2");
        assertThat(w2.attemptNumber()).isEqualTo(2);
        Map<String, Object> current = row(delivery);

        DeliveryAttemptRecorder.RecordResult late = recorder.record(w1, ok(), Instant.now());

        assertThat(late.applied()).isFalse();
        assertThat(row(delivery)).isEqualTo(current);
        assertThat(current.get("status")).isEqualTo("IN_FLIGHT");
        assertThat(current.get("lease_owner")).isEqualTo("w2");
        assertThat(attempts(delivery)).extracting(a -> a.get("error_class")).containsExactly("UNKNOWN");

        // The current holder still records normally.
        assertThat(recorder.record(w2, ok(), Instant.now()).applied()).isTrue();
        assertThat(row(delivery).get("status")).isEqualTo("SUCCEEDED");
        assertThat(attempts(delivery)).hasSize(2);
    }

    @Test
    @DisplayName("B: the old worker's failure is rejected the same way, and cannot schedule a retry")
    void staleFailureCannotOverwriteANewerClaim() {
        UUID delivery = job();
        ClaimedDelivery w1 = claimOnly(delivery, "w1");
        expireLease(delivery);
        recorder.recoverExpiredLeases(100);
        makeDue(delivery);
        claimOnly(delivery, "w2");
        Map<String, Object> current = row(delivery);

        assertThat(recorder.record(w1, serverError(), Instant.now()).applied()).isFalse();
        assertThat(recorder.record(w1, new AttemptOutcome(400, AttemptErrorClass.HTTP_CLIENT_ERROR, false, 3, null),
                Instant.now()).applied()).isFalse();

        assertThat(row(delivery)).isEqualTo(current);
        assertThat(attempts(delivery)).hasSize(1);
    }

    @Test
    @DisplayName("B: the same worker's result for an older attempt is rejected by the attempt number")
    void sameOwnerOlderAttemptIsRejected() {
        UUID delivery = job();
        ClaimedDelivery attempt1 = claimOnly(delivery, "w1");
        expireLease(delivery);
        recorder.recoverExpiredLeases(100);
        makeDue(delivery);
        ClaimedDelivery attempt2 = claimOnly(delivery, "w1");
        assertThat(attempt2.leaseOwner()).isEqualTo(attempt1.leaseOwner());
        Map<String, Object> current = row(delivery);

        assertThat(recorder.record(attempt1, ok(), Instant.now()).applied()).isFalse();

        assertThat(row(delivery)).isEqualTo(current);
        assertThat(recorder.record(attempt2, ok(), Instant.now()).applied()).isTrue();
    }

    @Test
    @DisplayName("B: a late or repeated result cannot reopen a finished delivery")
    void resultAfterTheEndIsRejected() {
        UUID delivery = job();
        ClaimedDelivery claim = claimOnly(delivery, "w1");
        assertThat(recorder.record(claim, ok(), Instant.now()).applied()).isTrue();
        Map<String, Object> finished = row(delivery);

        assertThat(recorder.record(claim, ok(), Instant.now()).applied()).isFalse();
        assertThat(recorder.record(claim, serverError(), Instant.now()).applied()).isFalse();

        assertThat(row(delivery)).isEqualTo(finished);
        assertThat(attempts(delivery)).hasSize(1);
    }

    @Test
    @DisplayName("B: a result whose lease was recovered (but not re-claimed) is rejected, leaving the recovery in place")
    void resultAfterRecoveryIsRejected() {
        UUID delivery = job();
        ClaimedDelivery claim = claimOnly(delivery, "w1");
        expireLease(delivery);
        recorder.recoverExpiredLeases(100);
        Map<String, Object> recovered = row(delivery);

        assertThat(recorder.record(claim, ok(), Instant.now()).applied()).isFalse();

        assertThat(row(delivery)).isEqualTo(recovered);
        assertThat(recovered.get("status")).isEqualTo("RETRY");
    }

    // ---- C. releaseClaim ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("C: releasing a first-attempt claim returns it to PENDING, schedule unchanged, no attempt row")
    void releaseReturnsTheJobToPending() {
        UUID delivery = job();
        Object scheduled = row(delivery).get("next_attempt_at");
        ClaimedDelivery claim = claimOnly(delivery, "w1");

        assertThat(recorder.release(claim)).isTrue();

        Map<String, Object> after = row(delivery);
        assertThat(after.get("status")).isEqualTo("PENDING");
        assertThat(after.get("attempt_count")).isEqualTo(0);
        assertThat(after.get("lease_owner")).isNull();
        assertThat(after.get("lease_expires_at")).isNull();
        assertThat(after.get("next_attempt_at")).isEqualTo(scheduled);
        assertThat(attempts(delivery)).isEmpty();
        // Another worker can claim it, as attempt 1 again.
        ClaimedDelivery again = claimOnly(delivery, "w2");
        assertThat(again.attemptNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("C: releasing a retry's claim returns it to RETRY with the previous attempt count")
    void releaseOfARetryReturnsToRetry() {
        UUID delivery = job();
        assertThat(recorder.record(claimOnly(delivery, "w1"), serverError(), Instant.now()).applied()).isTrue();
        makeDue(delivery);
        Object scheduled = row(delivery).get("next_attempt_at");
        ClaimedDelivery retry = claimOnly(delivery, "w1");
        assertThat(retry.attemptNumber()).isEqualTo(2);

        assertThat(recorder.release(retry)).isTrue();

        Map<String, Object> after = row(delivery);
        assertThat(after.get("status")).isEqualTo("RETRY");
        assertThat(after.get("attempt_count")).isEqualTo(1);
        assertThat(after.get("next_attempt_at")).isEqualTo(scheduled);
        assertThat(attempts(delivery)).hasSize(1);
    }

    @Test
    @DisplayName("C: another worker, or an outdated attempt number, cannot release the current claim")
    void releaseIsFencedByOwnerAndAttempt() {
        UUID delivery = job();
        ClaimedDelivery claim = claimOnly(delivery, "w1");
        Map<String, Object> current = row(delivery);

        ClaimedDelivery otherOwner = new ClaimedDelivery(delivery, claim.inboundEventId(), claim.endpointId(),
                claim.merchantId(), claim.eventType(), claim.attemptNumber(), "w2", claim.leaseExpiresAt());
        ClaimedDelivery otherAttempt = new ClaimedDelivery(delivery, claim.inboundEventId(), claim.endpointId(),
                claim.merchantId(), claim.eventType(), claim.attemptNumber() + 1, "w1", claim.leaseExpiresAt());

        assertThat(recorder.release(otherOwner)).isFalse();
        assertThat(recorder.release(otherAttempt)).isFalse();
        assertThat(row(delivery)).isEqualTo(current);
    }

    @Test
    @DisplayName("C: a stale claim cannot release the newer claim of the same delivery")
    void staleClaimCannotReleaseANewerClaim() {
        UUID delivery = job();
        ClaimedDelivery old = claimOnly(delivery, "w1");
        expireLease(delivery);
        recorder.recoverExpiredLeases(100);
        makeDue(delivery);
        claimOnly(delivery, "w2");
        Map<String, Object> current = row(delivery);

        assertThat(recorder.release(old)).isFalse();

        assertThat(row(delivery)).isEqualTo(current);
    }

    @Test
    @DisplayName("C: releasing after the delivery finished or was recovered changes nothing")
    void releaseAfterTheEndChangesNothing() {
        UUID done = job();
        UUID dead = job();
        UUID recovered = job();
        Map<UUID, ClaimedDelivery> claims = claimAll("w1");
        ClaimedDelivery doneClaim = claims.get(done);
        ClaimedDelivery deadClaim = claims.get(dead);
        ClaimedDelivery recoveredClaim = claims.get(recovered);
        recorder.record(doneClaim, ok(), Instant.now());
        recorder.record(deadClaim, new AttemptOutcome(404, AttemptErrorClass.HTTP_CLIENT_ERROR, false, 3, null), Instant.now());
        expireLease(recovered);
        recorder.recoverExpiredLeases(100);
        Map<String, Object> doneBefore = row(done), deadBefore = row(dead), recoveredBefore = row(recovered);

        assertThat(recorder.release(doneClaim)).isFalse();
        assertThat(recorder.release(deadClaim)).isFalse();
        assertThat(recorder.release(recoveredClaim)).isFalse();

        assertThat(row(done)).isEqualTo(doneBefore);
        assertThat(row(dead)).isEqualTo(deadBefore);
        assertThat(row(recovered)).isEqualTo(recoveredBefore);
    }

    // ---- D. concurrent claimers ----------------------------------------------------------------------------------

    @Test
    @DisplayName("D: a job locked by one claimer's open transaction is skipped by another, which takes the rest")
    void overlappingClaimersGetDisjointJobs() throws Exception {
        List<UUID> all = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            all.add(job());
        }
        CountDownLatch claimed = new CountDownLatch(1), commit = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<List<ClaimedDelivery>> first = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
                List<ClaimedDelivery> mine = jobs.claimDue("w1", LEASE, 3, MAX_ATTEMPTS);
                claimed.countDown();
                await(commit); // keep the rows locked while the second claimer runs
                return mine;
            }), pool);
            assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();

            List<ClaimedDelivery> second = tx.execute(status -> jobs.claimDue("w2", LEASE, 10, MAX_ATTEMPTS));

            commit.countDown();
            List<ClaimedDelivery> firstClaims = first.get(10, TimeUnit.SECONDS);
            assertThat(firstClaims).hasSize(3);
            assertThat(second).hasSize(3);
            Set<UUID> firstIds = ids(firstClaims), secondIds = ids(second);
            assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
            assertThat(union(firstIds, secondIds)).containsExactlyInAnyOrderElementsOf(all);
            for (ClaimedDelivery c : firstClaims) {
                assertThat(row(c.deliveryId()).get("lease_owner")).isEqualTo("w1");
            }
            for (ClaimedDelivery c : second) {
                assertThat(row(c.deliveryId()).get("lease_owner")).isEqualTo("w2");
            }
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("D: while the oldest job of an order is being claimed, nobody can claim that order's next job")
    void aLockedJobStillBlocksItsSuccessor() throws Exception {
        UUID order = UUID.randomUUID();
        UUID older = job(endpoint, order, 10);
        UUID newer = job(endpoint, order, 5);
        UUID unrelated = job();
        CountDownLatch claimed = new CountDownLatch(1), commit = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<List<ClaimedDelivery>> first = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
                List<ClaimedDelivery> mine = jobs.claimDue("w1", LEASE, 1, MAX_ATTEMPTS);
                claimed.countDown();
                await(commit);
                return mine;
            }), pool);
            assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();

            List<ClaimedDelivery> second = tx.execute(status -> jobs.claimDue("w2", LEASE, 10, MAX_ATTEMPTS));

            commit.countDown();
            assertThat(ids(first.get(10, TimeUnit.SECONDS))).containsExactly(older);
            assertThat(ids(second)).containsExactly(unrelated).doesNotContain(newer);
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }
        assertThat(row(newer).get("status")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("D: many concurrent workers deliver everything exactly once, in order per order, never two at a time")
    void concurrentWorkersKeepFifoAndExactlyOnceClaims() throws Exception {
        int orders = 6, perOrder = 4, workers = 5;
        Map<UUID, List<UUID>> expected = new ConcurrentHashMap<>();
        for (int o = 0; o < orders; o++) {
            UUID order = UUID.randomUUID();
            List<UUID> sequence = new ArrayList<>();
            for (int e = 0; e < perOrder; e++) {
                sequence.add(job(endpoint, order, 100 - e)); // older first
            }
            expected.put(order, sequence);
        }
        Map<UUID, UUID> orderOf = new ConcurrentHashMap<>();
        expected.forEach((order, sequence) -> sequence.forEach(d -> orderOf.put(d, order)));
        List<UUID> claimLog = Collections.synchronizedList(new ArrayList<>());
        List<String> violations = Collections.synchronizedList(new ArrayList<>());
        CyclicBarrier start = new CyclicBarrier(workers);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<CompletableFuture<Void>> running = new ArrayList<>();
        for (int w = 0; w < workers; w++) {
            String owner = "w" + w;
            running.add(CompletableFuture.runAsync(() -> {
                await(start);
                for (int idle = 0; idle < 50; ) {
                    List<ClaimedDelivery> batch = recorder.claim(owner, 3);
                    if (batch.isEmpty()) {
                        idle++;
                        Thread.onSpinWait();
                        continue;
                    }
                    idle = 0;
                    for (ClaimedDelivery job : batch) {
                        claimLog.add(job.deliveryId());
                        // Terminal states are permanent, so this check cannot race: at claim time every older job of
                        // the same endpoint and order must already be finished.
                        Integer olderActive = jdbc.queryForObject("""
                                SELECT count(*) FROM webhook_deliveries o
                                JOIN inbound_events oev ON oev.event_id = o.inbound_event_id
                                JOIN webhook_deliveries d ON d.delivery_id = ?
                                JOIN inbound_events ev ON ev.event_id = d.inbound_event_id
                                WHERE o.endpoint_id = d.endpoint_id AND oev.order_id = ev.order_id
                                  AND oev.occurred_at < ev.occurred_at AND o.status NOT IN ('SUCCEEDED', 'DEAD')
                                """, Integer.class, job.deliveryId());
                        if (olderActive != 0) {
                            violations.add("claimed " + job.deliveryId() + " with " + olderActive + " older active");
                        }
                        if (!recorder.record(job, ok(), Instant.now()).applied()) {
                            violations.add("own fresh claim rejected: " + job.deliveryId());
                        }
                    }
                }
            }, pool));
        }
        CompletableFuture.allOf(running.toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(violations).isEmpty();
        assertThat(claimLog).hasSize(orders * perOrder).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_deliveries WHERE status = 'SUCCEEDED' AND attempt_count = 1",
                Integer.class)).isEqualTo(orders * perOrder);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_delivery_attempts", Integer.class)).isEqualTo(orders * perOrder);
        // Per order, the claims happened oldest first.
        expected.forEach((order, sequence) -> assertThat(claimLog.stream().filter(d -> orderOf.get(d).equals(order)).toList())
                .containsExactlyElementsOf(sequence));
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private static Set<UUID> ids(List<ClaimedDelivery> claims) {
        Set<UUID> ids = new HashSet<>();
        claims.forEach(c -> ids.add(c.deliveryId()));
        return ids;
    }

    private static Set<UUID> union(Set<UUID> a, Set<UUID> b) {
        Set<UUID> all = new HashSet<>(a);
        all.addAll(b);
        return all;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(20, TimeUnit.SECONDS);
        } catch (Exception failed) {
            throw new IllegalStateException(failed);
        }
    }
}
