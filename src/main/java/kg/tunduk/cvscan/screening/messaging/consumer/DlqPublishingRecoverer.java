package kg.tunduk.cvscan.screening.messaging.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.tunduk.cvscan.screening.dto.kafka.DlqEvent;
import kg.tunduk.cvscan.screening.generated.rest.model.ErrorResponseDetailsInner;
import kg.tunduk.cvscan.screening.exception.NonRetryableEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Единая точка для ошибок "сразу без повторов" и "повторы исчерпаны":
 * оба пути идут через один и тот же callback recoverer {@link org.springframework.kafka.listener.DefaultErrorHandler},
 * что гарантирует единый формат DLQ-конверта и то, что плохое сообщение
 * никогда не блокирует следующее в партиции (контейнер в любом случае коммитит дальше).
 */
public class DlqPublishingRecoverer implements ConsumerRecordRecoverer {

    private static final Logger log = LoggerFactory.getLogger(DlqPublishingRecoverer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String dlqTopic;

    public DlqPublishingRecoverer(final KafkaTemplate<String, String> kafkaTemplate, final ObjectMapper objectMapper, final String dlqTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.dlqTopic = dlqTopic;
    }

    @Override
    public void accept(final ConsumerRecord<?, ?> record, final Exception exception) {
        final Optional<NonRetryableEventException> nonRetryable = findNonRetryable(exception);
        final String errorCode = nonRetryable.map(NonRetryableEventException::getErrorCode).orElse("PROCESSING_ERROR");
        final String errorMessage = nonRetryable.map(Throwable::getMessage).orElseGet(() -> rootMessage(exception));
        final List<ErrorResponseDetailsInner> details = nonRetryable.map(NonRetryableEventException::getDetails).orElse(List.of());

        final String rawValue = record.value() == null ? null : record.value().toString();
        final Object originalPayload = tryParseJson(rawValue);

        final DlqEvent dlqEvent = new DlqEvent(originalPayload, errorCode, errorMessage, details, Instant.now(),
                record.topic(), record.partition(), record.offset());

        try {
            final String key = record.key() == null ? null : record.key().toString();
            kafkaTemplate.send(dlqTopic, key, objectMapper.writeValueAsString(dlqEvent));
            log.warn("Published to DLQ topic={} errorCode={} sourceTopic={} partition={} offset={}",
                    dlqTopic, errorCode, record.topic(), record.partition(), record.offset());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DLQ event for sourceTopic={} partition={} offset={}",
                    record.topic(), record.partition(), record.offset(), e);
        }
    }

    private Optional<NonRetryableEventException> findNonRetryable(final Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NonRetryableEventException nre) {
                return Optional.of(nre);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    private String rootMessage(final Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    private Object tryParseJson(final String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            return raw;
        }
    }
}
