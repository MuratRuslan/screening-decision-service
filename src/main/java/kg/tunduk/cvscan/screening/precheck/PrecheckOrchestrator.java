package kg.tunduk.cvscan.screening.precheck;

import kg.tunduk.cvscan.screening.generated.kafka.CvParsedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Runs all registered {@link PrecheckCheck}s in parallel on virtual threads, each bounded by
 * its own timeout. Concurrency toward the (simulated) external dependency is bounded by a
 * {@link Semaphore}, not the executor itself: virtual threads are cheap to create by the
 * thousand, so limiting thread count would not actually protect a downstream dependency -
 * the semaphore caps concurrent *calls* regardless of how many virtual threads are waiting
 * on {@code acquire()}, and {@code Semaphore.acquire()} parks the virtual thread without
 * pinning its carrier (unlike a {@code synchronized} block, deliberately avoided here).
 */
@Component
public class PrecheckOrchestrator {

    private final ExecutorService precheckExecutor;
    private final List<PrecheckCheck> checks;
    private final Semaphore semaphore;
    private final long timeoutMs;

    public PrecheckOrchestrator(@Qualifier("precheckExecutor") ExecutorService precheckExecutor,
                                 List<PrecheckCheck> checks,
                                 @Value("${app.precheck.max-concurrent-calls}") int maxConcurrentCalls,
                                 @Value("${app.precheck.timeout-ms}") long timeoutMs) {
        this.precheckExecutor = precheckExecutor;
        this.checks = checks;
        this.semaphore = new Semaphore(maxConcurrentCalls);
        this.timeoutMs = timeoutMs;
    }

    public List<PrecheckResult> runAll(CvParsedEvent event) {
        List<CompletableFuture<PrecheckResult>> futures = checks.stream()
                .map(check -> runOne(check, event))
                .toList();

        return futures.stream().map(CompletableFuture::join).toList();
    }

    private CompletableFuture<PrecheckResult> runOne(PrecheckCheck check, CvParsedEvent event) {
        return CompletableFuture
                .supplyAsync(() -> executeBounded(check, event), precheckExecutor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> new PrecheckResult(check.name(), PrecheckStatus.TIMEOUT,
                        "Тайм-аут допроверки (" + timeoutMs + " ms)", timeoutMs));
    }

    private PrecheckResult executeBounded(PrecheckCheck check, CvParsedEvent event) {
        long start = System.nanoTime();
        try {
            semaphore.acquire();
            try {
                return check.run(event).withDuration(elapsedMs(start));
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PrecheckResult(check.name(), PrecheckStatus.FAILED, "Прервано во время ожидания семафора", elapsedMs(start));
        } catch (Exception e) {
            // A single check's failure must never take down the whole consumer.
            return new PrecheckResult(check.name(), PrecheckStatus.FAILED, "Ошибка допроверки: " + e.getMessage(), elapsedMs(start));
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
