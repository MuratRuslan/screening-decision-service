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
    public DlqPublishingRecoverer dlqPublishingRecoverer(final KafkaTemplate<String, String> kafkaTemplate,
                                                           final ObjectMapper objectMapper,
                                                           @Value("${app.kafka.topics.decision-dlq}") final String dlqTopic) {
        return new DlqPublishingRecoverer(kafkaTemplate, objectMapper, dlqTopic);
    }

    /**
     * Kafka автоконфигурация Spring Boot сама подключает единственный бин {@link CommonErrorHandler}
     * к автоконфигурированной {@code ConcurrentKafkaListenerContainerFactory}.
     * Используется блокирующий (синхронный) retry с экспоненциальным backoff, ограниченный
     * max-elapsed-time - это проще и достаточно по сравнению с неблокирующей retry-topic
     * инфраструктурой. И путь "сразу не подлежит retry", и путь "retry исчерпаны" попадают
     * в один и тот же DLQ recoverer.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(final DlqPublishingRecoverer dlqPublishingRecoverer,
                                                 @Value("${app.kafka.consumer.retry.initial-interval-ms}") final long initialIntervalMs,
                                                 @Value("${app.kafka.consumer.retry.multiplier}") final double multiplier,
                                                 @Value("${app.kafka.consumer.retry.max-interval-ms}") final long maxIntervalMs,
                                                 @Value("${app.kafka.consumer.retry.max-elapsed-time-ms}") final long maxElapsedTimeMs) {
        final ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, multiplier);
        backOff.setMaxInterval(maxIntervalMs);
        backOff.setMaxElapsedTime(maxElapsedTimeMs);

        final DefaultErrorHandler errorHandler = new DefaultErrorHandler(dlqPublishingRecoverer, backOff);
        errorHandler.addNotRetryableExceptions(NonRetryableEventException.class);
        return errorHandler;
    }
}
