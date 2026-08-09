package kg.tunduk.test.senior.screeningdecisionservice.precheck;

import kg.tunduk.test.senior.screeningdecisionservice.generated.kafka.CvParsedEvent;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Simulates checking the candidate's name against a conditional sanctions/blocklist. */
@Component
public class SanctionsCheck implements PrecheckCheck {

    /** A placeholder in-memory blocklist - a real integration would call an external registry. */
    private static final Set<String> BLOCKLIST = Set.of("тестовый нарушитель", "sanctioned test person");

    @Override
    public String name() {
        return "sanctions-check";
    }

    @Override
    public PrecheckResult run(CvParsedEvent event) {
        simulateLookupLatency();

        String name = event.getName() == null ? "" : event.getName().toLowerCase(Locale.ROOT).trim();
        boolean blocked = BLOCKLIST.stream().anyMatch(name::contains);
        if (blocked) {
            return new PrecheckResult(name(), PrecheckStatus.FAILED, "Имя найдено в условном списке блокировок", 0);
        }
        return new PrecheckResult(name(), PrecheckStatus.PASSED, "Совпадений в списке блокировок нет", 0);
    }

    private void simulateLookupLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(20, 70));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
