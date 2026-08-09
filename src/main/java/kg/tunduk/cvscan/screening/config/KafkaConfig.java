package kg.tunduk.cvscan.screening.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.tunduk.cvscan.screening.exception.NonRetryableEventException;
import kg.tunduk.cvscan.screening.messaging.consumer.DlqPublishingRecoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public DlqPublishingRecoverer dlqPublishingRecoverer(KafkaTemplate<String, String> kafkaTemplate,
                                                           ObjectMapper objectMapper,
                                                           @Value("${app.kafka.topics.decision-dlq}") String dlqTopic) {
        return new DlqPublishingRecoverer(kafkaTemplate, objectMapper, dlqTopic);
    }

    /**
     * Spring Boot's Kafka autoconfiguration wires the single {@link CommonErrorHandler} bean
     * into its auto-configured {@code ConcurrentKafkaListenerContainerFactory} automatically.
     * Uses blocking (synchronous) retry with exponential backoff, bounded by max-elapsed-time -
     * simple and sufficient here versus non-blocking retry-topic infrastructure. Both the
     * "immediately non-retryable" and "retries exhausted" paths land in the same DLQ recoverer.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(DlqPublishingRecoverer dlqPublishingRecoverer,
                                                 @Value("${app.kafka.consumer.retry.initial-interval-ms}") long initialIntervalMs,
                                                 @Value("${app.kafka.consumer.retry.multiplier}") double multiplier,
                                                 @Value("${app.kafka.consumer.retry.max-interval-ms}") long maxIntervalMs,
                                                 @Value("${app.kafka.consumer.retry.max-elapsed-time-ms}") long maxElapsedTimeMs) {
        ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, multiplier);
        backOff.setMaxInterval(maxIntervalMs);
        backOff.setMaxElapsedTime(maxElapsedTimeMs);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(dlqPublishingRecoverer, backOff);
        errorHandler.addNotRetryableExceptions(NonRetryableEventException.class);
        return errorHandler;
    }
}
