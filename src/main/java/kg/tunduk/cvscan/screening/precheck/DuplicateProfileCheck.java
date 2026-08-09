package kg.tunduk.cvscan.screening.precheck;

import kg.tunduk.cvscan.screening.generated.kafka.CvParsedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Имитирует внешний поиск похожего существующего профиля кандидата по email/телефону.
 * Реального хранилища дублей профилей в этой задаче нет; "положительный" случай срабатывает
 * на email с `+`-алиасом (частый в реальности сигнал дублирования, например name+tag@domain) -
 * просто чтобы ветку WARNING можно было проверить в тестах и демо.
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
