package kg.tunduk.cvscan.screening.precheck;

import kg.tunduk.cvscan.screening.generated.kafka.CvParsedEvent;

/**
 * Одна I/O-зависимая допроверка перед принятием решения. Реализации сами
 * имитируют задержку и не должны бросать исключения - {@link PrecheckOrchestrator}
 * тоже от этого защищается, но лучше явно вернуть {@link PrecheckStatus#FAILED},
 * чем полагаться на catch-all оркестратора.
 */
public interface PrecheckCheck {

    String name();

    PrecheckResult run(CvParsedEvent event);
}
