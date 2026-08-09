package kg.tunduk.cvscan.screening.precheck;

import kg.tunduk.cvscan.screening.generated.kafka.CvParsedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates an external lookup for a similar existing candidate profile by email/phone.
 * No real duplicate-profile store exists for this task; the "positive" case is triggered by
 * a `+`-aliased email (a common real-world dedupe signal, e.g. name+tag@domain), purely so
 * the WARNING branch is exercisable in tests and manual demos.
 */
@Component
public class DuplicateProfileCheck implements PrecheckCheck {

    @Override
    public String name() {
        return "duplicate-profile-check";
    }

    @Override
    public PrecheckResult run(CvParsedEvent event) {
        simulateLookupLatency();

        String email = event.getEmail() == null ? "" : event.getEmail();
        boolean looksAliased = email.contains("+");
        if (looksAliased) {
            return new PrecheckResult(name(), PrecheckStatus.WARNING,
                    "Похожий профиль по email-алиасу: " + email, 0);
        }
        return new PrecheckResult(name(), PrecheckStatus.PASSED, "Похожих профилей не найдено", 0);
    }

    private void simulateLookupLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(20, 90));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
