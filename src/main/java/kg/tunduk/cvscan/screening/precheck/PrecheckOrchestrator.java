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
 * Запускает все зарегистрированные {@link PrecheckCheck} параллельно на виртуальных
 * потоках, каждый со своим тайм-аутом. Конкурентность к (имитируемой) внешней зависимости
 * ограничивается {@link Semaphore}, а не самим executor'ом: виртуальные потоки дёшево
 * создавать тысячами, поэтому ограничение числа потоков не защитило бы зависимость -
 * семафор ограничивает именно количество одновременных *вызовов* независимо от того,
 * сколько виртуальных потоков ждут на {@code acquire()}, а {@code Semaphore.acquire()}
 * паркует виртуальный поток, не блокируя его carrier-поток (в отличие от блока
 * {@code synchronized}, который здесь намеренно не используется).
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
            // Падение одной проверки не должно ронять весь consumer.
            return new PrecheckResult(check.name(), PrecheckStatus.FAILED, "Ошибка допроверки: " + e.getMessage(), elapsedMs(start));
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
