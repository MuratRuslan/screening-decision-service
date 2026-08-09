package kg.tunduk.test.senior.screeningdecisionservice.messaging.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.tunduk.test.senior.screeningdecisionservice.dto.kafka.DlqEvent;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.ErrorResponseDetailsInner;
import kg.tunduk.test.senior.screeningdecisionservice.exception.NonRetryableEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The single funnel for both "immediately non-retryable" and "retries exhausted" failures:
 * both paths go through the same {@link org.springframework.kafka.listener.DefaultErrorHandler}
 * recoverer callback, guaranteeing a uniform DLQ envelope and guaranteeing that a bad message
 * never blocks the next one on the partition (the container commits past it either way).
 */
public class DlqPublishingRecoverer implements ConsumerRecordRecoverer {

    private static final Logger log = LoggerFactory.getLogger(DlqPublishingRecoverer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String dlqTopic;

    public DlqPublishingRecoverer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, String dlqTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.dlqTopic = dlqTopic;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        Optional<NonRetryableEventException> nonRetryable = findNonRetryable(exception);
        String errorCode = nonRetryable.map(NonRetryableEventException::getErrorCode).orElse("PROCESSING_ERROR");
        String errorMessage = nonRetryable.map(Throwable::getMessage).orElseGet(() -> rootMessage(exception));
        List<ErrorResponseDetailsInner> details = nonRetryable.map(NonRetryableEventException::getDetails).orElse(List.of());

        String rawValue = record.value() == null ? null : record.value().toString();
        Object originalPayload = tryParseJson(rawValue);

        DlqEvent dlqEvent = new DlqEvent(originalPayload, errorCode, errorMessage, details, Instant.now(),
                record.topic(), record.partition(), record.offset());

        try {
            String key = record.key() == null ? null : record.key().toString();
            kafkaTemplate.send(dlqTopic, key, objectMapper.writeValueAsString(dlqEvent));
            log.warn("Published to DLQ topic={} errorCode={} sourceTopic={} partition={} offset={}",
                    dlqTopic, errorCode, record.topic(), record.partition(), record.offset());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DLQ event for sourceTopic={} partition={} offset={}",
                    record.topic(), record.partition(), record.offset(), e);
        }
    }

    private Optional<NonRetryableEventException> findNonRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NonRetryableEventException nre) {
                return Optional.of(nre);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    private Object tryParseJson(String raw) {
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
