package kg.tunduk.cvscan.screening.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/**
 * Небольшой помощник для добавления бизнес-атрибутов (candidateId, decisionId, ...)
 * к текущему активному span'у - Spring MVC и Spring Kafka (при {@code
 * observation-enabled: true}) сами уже создают span'ы; этот класс только обогащает их.
 * Намеренно терпим к отсутствию текущего span'а (трейсинг выключен, sampled out или
 * вызов вне scope span'а), чтобы вызывающему коду не нужны были свои null-проверки.
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
