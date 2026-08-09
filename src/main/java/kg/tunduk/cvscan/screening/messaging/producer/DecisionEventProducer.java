package kg.tunduk.cvscan.screening.messaging.producer;

import io.micrometer.tracing.Tracer;
import kg.tunduk.cvscan.screening.observability.Spans;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Единственное место, которому разрешено публиковать бизнес-события в Kafka - бизнес-код
 * никогда не вызывает KafkaTemplate напрямую, он только пишет строку в outbox; {@link
 * kg.tunduk.cvscan.screening.outbox.OutboxPublisher} - единственный
 * вызывающий этот класс.
 */
@Component
public class DecisionEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Tracer tracer;

    public DecisionEventProducer(KafkaTemplate<String, String> kafkaTemplate, Tracer tracer) {
        this.kafkaTemplate = kafkaTemplate;
        this.tracer = tracer;
    }

    /** Отправляет синхронно, блокируясь до {@code timeoutMs} в ожидании подтверждения от брокера. */
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
