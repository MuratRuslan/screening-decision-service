package kg.tunduk.cvscan.screening.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/**
 * Small helper for adding business-domain attributes (candidateId, decisionId, ...) to
 * whichever span is currently active - Spring MVC and Spring Kafka (once {@code
 * observation-enabled: true}) already create the spans themselves; this only enriches them.
 * Deliberately tolerant of there being no current span (tracing disabled, sampled out, or
 * called outside any span scope) so call sites never need their own null checks.
 */
public final class Spans {

    private Spans() {
    }

    public static void tag(Tracer tracer, String key, String value) {
        if (tracer == null || value == null) {
            return;
        }
        Span span = tracer.currentSpan();
        if (span != null) {
            span.tag(key, value);
        }
    }
}
