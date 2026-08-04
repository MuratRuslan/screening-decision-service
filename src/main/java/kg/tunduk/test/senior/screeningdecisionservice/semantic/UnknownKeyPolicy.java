package kg.tunduk.test.senior.screeningdecisionservice.semantic;

/**
 * What to do with a {@code criteria[].key} that the semantic catalog has no alias for.
 * Configured via {@code app.semantic.unknown-key-policy}, applied by the consumer pipeline
 * (an unrelated unknown criterion should not by default block an otherwise valid screening).
 */
public enum UnknownKeyPolicy {
    /** Record unmapped keys into the audit payload and continue processing (default). */
    AUDIT,
    /** Route the whole event to the DLQ with errorCode=UNKNOWN_CRITERION_KEY. */
    DLQ
}
