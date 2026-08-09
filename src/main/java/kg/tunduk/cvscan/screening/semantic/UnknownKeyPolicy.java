package kg.tunduk.cvscan.screening.semantic;

/**
 * Что делать с {@code criteria[].key}, для которого в семантическом каталоге нет алиаса.
 * Настраивается через {@code app.semantic.unknown-key-policy}, применяется пайплайном
 * консьюмера (посторонний неизвестный критерий по умолчанию не должен блокировать иначе
 * валидный скрининг).
 */
public enum UnknownKeyPolicy {
    /** Записать неизвестные ключи в payload аудита и продолжить обработку (по умолчанию). */
    AUDIT,
    /** Отправить всё событие в DLQ с errorCode=UNKNOWN_CRITERION_KEY. */
    DLQ
}
