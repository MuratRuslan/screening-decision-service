package kg.tunduk.test.senior.screeningdecisionservice.messaging.producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The only place allowed to publish business events to Kafka - business code never calls
 * KafkaTemplate directly, it only ever writes an outbox row; {@link
 * kg.tunduk.test.senior.screeningdecisionservice.outbox.OutboxPublisher} is this class's
 * sole caller.
 */
@Component
public class DecisionEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public DecisionEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Sends synchronously, blocking up to {@code timeoutMs} for the broker ack. */
    public void send(String topic, String key, String payload, long timeoutMs) {
        try {
            kafkaTemplate.send(topic, key, payload).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaSendException("Interrupted while sending to topic " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new KafkaSendException("Failed to send to topic " + topic, e);
        }
    }
}
