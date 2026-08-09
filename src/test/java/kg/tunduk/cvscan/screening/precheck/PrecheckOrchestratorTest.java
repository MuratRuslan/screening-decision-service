package kg.tunduk.cvscan.screening.precheck;

import kg.tunduk.cvscan.screening.generated.kafka.CvParsedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class PrecheckOrchestratorTest {

    private static final CvParsedEvent EVENT = buildEvent();

    private static CvParsedEvent buildEvent() {
        CvParsedEvent event = new CvParsedEvent();
        event.setEventId(UUID.randomUUID());
        event.setCandidateId("candidate-1");
        event.setParsedAt(Instant.now());
        event.setName("Тест Тестов");
        event.setPosition("java-senior");
        event.setEmail("test@example.com");
        event.setVerdict(CvParsedEvent.Verdict.FIT);
        event.setCriteria(List.of());
        event.setExperience(List.of());
        event.setQuestions(List.of());
        return event;
    }

    private record SleepingCheck(String name, long sleepMs, PrecheckStatus status) implements PrecheckCheck {
        @Override
        public PrecheckResult run(CvParsedEvent event) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new PrecheckResult(name, status, "ok", 0);
        }
    }

    private record ThrowingCheck(String name) implements PrecheckCheck {
        @Override
        public PrecheckResult run(CvParsedEvent event) {
            throw new RuntimeException("boom");
        }
    }

    @Test
    void checksRunInParallelNotSequentially() {
        List<PrecheckCheck> checks = List.of(
                new SleepingCheck("a", 200, PrecheckStatus.PASSED),
                new SleepingCheck("b", 200, PrecheckStatus.PASSED));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PrecheckOrchestrator orchestrator = new PrecheckOrchestrator(executor, checks, 10, 5000);

            long start = System.nanoTime();
            List<PrecheckResult> results = orchestrator.runAll(EVENT);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(results).hasSize(2);
            assertThat(results).allSatisfy(r -> assertThat(r.status()).isEqualTo(PrecheckStatus.PASSED));
            // Sequential would be >= 400ms; parallel should be close to the single 200ms sleep.
            assertThat(elapsedMs).isLessThan(350);
        }
    }

    @Test
    void slowCheckTimesOutWithoutBlockingTheOverallResult() {
        List<PrecheckCheck> checks = List.of(new SleepingCheck("slow", 2000, PrecheckStatus.PASSED));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PrecheckOrchestrator orchestrator = new PrecheckOrchestrator(executor, checks, 10, 200);

            long start = System.nanoTime();
            List<PrecheckResult> results = orchestrator.runAll(EVENT);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo(PrecheckStatus.TIMEOUT);
            assertThat(elapsedMs).isLessThan(1000);
        }
    }

    @Test
    void oneCheckThrowingDoesNotAffectTheOthers() {
        List<PrecheckCheck> checks = List.of(
                new ThrowingCheck("throwing-check"),
                new SleepingCheck("ok-check", 10, PrecheckStatus.PASSED));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PrecheckOrchestrator orchestrator = new PrecheckOrchestrator(executor, checks, 10, 5000);

            List<PrecheckResult> results = orchestrator.runAll(EVENT);

            assertThat(results).hasSize(2);
            assertThat(byName(results, "throwing-check").status()).isEqualTo(PrecheckStatus.FAILED);
            assertThat(byName(results, "ok-check").status()).isEqualTo(PrecheckStatus.PASSED);
        }
    }

    @Test
    void semaphorePermitOfOneSerializesConcurrentCalls() {
        List<PrecheckCheck> checks = List.of(
                new SleepingCheck("a", 150, PrecheckStatus.PASSED),
                new SleepingCheck("b", 150, PrecheckStatus.PASSED));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            PrecheckOrchestrator orchestrator = new PrecheckOrchestrator(executor, checks, 1, 5000);

            long start = System.nanoTime();
            orchestrator.runAll(EVENT);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            // With only 1 permit, the two 150ms calls are forced serial: ~300ms, not ~150ms.
            assertThat(elapsedMs).isGreaterThanOrEqualTo(280);
        }
    }

    private static PrecheckResult byName(List<PrecheckResult> results, String name) {
        return results.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }
}
