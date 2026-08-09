package kg.tunduk.cvscan.screening.dto.kafka;

import kg.tunduk.cvscan.screening.generated.rest.model.ErrorResponseDetailsInner;

import java.time.Instant;
import java.util.List;

/**
 * Публикуется в {@code screening.decision.dlq}. {@code originalPayload} - это либо
 * распарсенный {@link com.fasterxml.jackson.databind.JsonNode} (вложенный как JSON), либо,
 * если сообщение вообще не было валидным JSON, сырая строка как есть.
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
