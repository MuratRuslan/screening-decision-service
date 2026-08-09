package kg.tunduk.cvscan.screening.outbox;

import kg.tunduk.cvscan.screening.messaging.producer.DecisionEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Опрашивает outbox_events и публикует строки NEW в Kafka. Захват (SELECT ... FOR UPDATE SKIP
 * LOCKED) и отправка-с-пометкой выполняются в одной транзакции за проход: именно блокировки
 * строк захватывающей транзакции делают SKIP LOCKED реальным захватом, поэтому вынос отправки
 * в отдельную транзакцию REQUIRES_NEW для каждой строки заставил бы вторую транзакцию
 * блокироваться на тех же блокировках, которые всё ещё держит первая. Компромисс в том, что
 * медленная отправка в Kafka чуть дольше держит блокировки строк батча - при таком размере
 * батча и интервале опроса это приемлемо, и задокументировано в ADR вместе с итоговой
 * семантикой at-least-once (если сбой произойдёт между ack брокера и коммитом этой транзакции,
 * строка после рестарта останется NEW и будет отправлена повторно; стабильный eventId
 * позволяет потребителям на другой стороне делать дедупликацию).
 * <p>
 * {@code fixedDelay} (а не {@code fixedRate}) гарантирует, что тики никогда не пересекаются -
 * это самая простая форма backpressure: медленный брокер Kafka естественным образом
 * замедляет частоту опроса вместо накопления параллельных батчей.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final DecisionEventProducer decisionEventProducer;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxPublisher(final OutboxRepository outboxRepository,
                            final DecisionEventProducer decisionEventProducer,
                            @Value("${app.outbox.batch-size}") final int batchSize,
                            @Value("${app.outbox.send-timeout-ms}") final long sendTimeoutMs) {
        this.outboxRepository = outboxRepository;
        this.decisionEventProducer = decisionEventProducer;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms}")
    @Transactional
    public void publishBatch() {
        final List<OutboxEvent> batch = outboxRepository.claimBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        for (final OutboxEvent event : batch) {
            try {
                decisionEventProducer.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload(), sendTimeoutMs);
                event.markSent();
                log.info("Outbox event sent id={} topic={} aggregateId={}", event.getId(), event.getTopic(), event.getAggregateId());
            } catch (Exception e) {
                event.markFailedAttempt(e.getMessage());
                log.warn("Outbox event send failed id={} topic={} retryCount={} error={}",
                        event.getId(), event.getTopic(), event.getRetryCount(), e.getMessage());
            }
        }
        // Сущности были загружены (и заблокированы) в этой же транзакции, поэтому
        // изменения полей выше автоматически сохранятся в БД при коммите - явный save() не нужен.
    }
}
