package kg.tunduk.cvscan.screening.dto.kafka;

import kg.tunduk.cvscan.screening.generated.rest.model.ErrorResponseDetailsInner;

import java.time.Instant;
import java.util.List;

/**
 * Published to {@code screening.decision.dlq}. {@code originalPayload} is either the
 * parsed {@link com.fasterxml.jackson.databind.JsonNode} (embedded as nested JSON) or,
 * if the message wasn't even parseable JSON, the raw string as-is.
 */
public record DlqEvent(
        Object originalPayload,
        String errorCode,
        String errorMessage,
        List<ErrorResponseDetailsInner> details,
        Instant failedAt,
        String sourceTopic,
        Integer partition,
        Long offset
) {
}
