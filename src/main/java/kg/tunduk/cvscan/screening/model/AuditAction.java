package kg.tunduk.cvscan.screening.model;

public enum AuditAction {
    CREATED,
    /** Зарезервировано для возможного будущего admin-эндпоинта "recompute"; сейчас не используется. */
    UPDATED_BY_REPLAY,
    OVERRIDDEN
}
