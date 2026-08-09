package kg.tunduk.cvscan.screening.messaging.producer;

import io.micrometer.tracing.Tracer;
import kg.tunduk.cvscan.screening.observability.Spans;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The only place allowed to publish business events to Kafka - business code never calls
 * KafkaTemplate directly, it only ever writes an outbox row; {@link
 * kg.tunduk.cvscan.screening.outbox.OutboxPublisher} is this class's
 * sole caller.
 */
@Component
public class DecisionEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Tracer tracer;

    public DecisionEventProducer(KafkaTemplate<String, String> kafkaTemplate, Tracer tracer) {
        this.kafkaTemplate = kafkaTemplate;
        this.tracer = tracer;
    }

    /** Sends synchronously, blocking up to {@code timeoutMs} for the broker ack. */
    public void send(String topic, String key, String payload, long timeoutMs) {
        Spans.tag(tracer, "kafka.topic", topic);
        Spans.tag(tracer, "kafka.key", key);
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
