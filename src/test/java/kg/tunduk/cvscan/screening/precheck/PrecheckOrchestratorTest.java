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
        final CvParsedEvent event = new CvParsedEvent();
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
        public PrecheckResult run(final CvParsedEvent event) {
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
        public PrecheckResult run(final CvParsedEvent event) {
            throw new RuntimeException("boom");
        }
    }

    @Test
    void checksRunInParallelNotSequentially() {
        final List<PrecheckCheck> checks = List.of(
                new SleepingCheck("a", 200, PrecheckStatus.PASSED),
                new SleepingCheck("b", 200, PrecheckStatus.PASSED));

        try (final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final PrecheckOrchestrator orchestrator = new PrecheckOrchestrator(executor, checks, 10, 5000);

            final long start = System.nanoTime();
            final List<PrecheckResult> results = orchestrator.runAll(EVENT);
            final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(results).hasSize(2);
            assertThat(results).allSatisfy(r -> assertThat(r.status()).isEqualTo(PrecheckStatus.PASSED));
            // Последовательно было бы >= 400мс; параллельно должно быть близко к одному sleep в 200мс.
            assertThat(elapsedMs).isLessThan(350);
        }
    }

    @Test
    void slowCheckTimesOutWithoutBlockingTheOverallResult() {
        final List<PrecheckCheck> checks = List.of(new SleepingCheck("slow", 2000, PrecheckStatus.PASSED));

        try (final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final PrecheckOrchestrator orchestrator = new PrecheckOrchestrator(executor, checks, 10, 200);

            final long start = System.nanoTime();
            final List<PrecheckResult> results = orchestrator.runAll(EVENT);
            final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(results).hasSize(1);
            assertThat(results.get(0).status()).isEqualTo(PrecheckStatus.TIMEOUT);
            assertThat(elapsedMs).isLessThan(1000);
        }
    }

    @Test
    void oneCheckThrowingDoesNotAffectTheOthers() {
        final List<PrecheckCheck> checks = List.of(
                new ThrowingCheck("throwing-check"),
                new SleepingCheck("ok-check", 10, PrecheckStatus.PASSED));

        try (final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final PrecheckOrchestrator orchestrator = new PrecheckOrchestrator(executor, checks, 10, 5000);

            final List<PrecheckResult> results = orchestrator.runAll(EVENT);

            assertThat(results).hasSize(2);
            assertThat(byName(results, "throwing-check").status()).isEqualTo(PrecheckStatus.FAILED);
            assertThat(byName(results, "ok-check").status()).isEqualTo(PrecheckStatus.PASSED);
        }
    }

    @Test
    void semaphorePermitOfOneSerializesConcurrentCalls() {
        final List<PrecheckCheck> checks = List.of(
                new SleepingCheck("a", 150, PrecheckStatus.PASSED),
                new SleepingCheck("b", 150, PrecheckStatus.PASSED));

        try (final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final PrecheckOrchestrator orchestrator = new PrecheckOrchestrator(executor, checks, 1, 5000);

            final long start = System.nanoTime();
            orchestrator.runAll(EVENT);
            final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            // При всего 1 разрешении оба вызова по 150мс выполняются последовательно: ~300мс, а не ~150мс.
            assertThat(elapsedMs).isGreaterThanOrEqualTo(280);
        }
    }

    private static PrecheckResult byName(final List<PrecheckResult> results, final String name) {
        return results.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }
}
