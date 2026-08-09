package kg.tunduk.cvscan.screening.model;

public enum AuditAction {
    CREATED,
    /** Reserved for a hypothetical future admin "recompute" endpoint; not currently emitted. */
    UPDATED_BY_REPLAY,
    OVERRIDDEN
}
