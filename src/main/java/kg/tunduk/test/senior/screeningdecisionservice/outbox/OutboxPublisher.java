package kg.tunduk.test.senior.screeningdecisionservice.outbox;

import kg.tunduk.test.senior.screeningdecisionservice.messaging.producer.DecisionEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls outbox_events and publishes NEW rows to Kafka. Claim (SELECT ... FOR UPDATE SKIP
 * LOCKED) and send-and-mark run inside a single transaction per poll: the claiming
 * transaction's row locks are what makes SKIP LOCKED work as a real claim, so splitting the
 * send into a separate REQUIRES_NEW transaction per row would make that second transaction
 * block on the very locks the first one is still holding. The trade-off is that a slow
 * Kafka send holds the batch's row locks a little longer - acceptable at this batch size/
 * poll interval, and documented in the ADR alongside the resulting at-least-once delivery
 * semantics (a crash between the broker ack and this transaction's commit means the row is
 * still NEW on restart and gets sent again; the stable eventId lets downstream consumers
 * dedupe).
 * <p>
 * {@code fixedDelay} (not {@code fixedRate}) ensures ticks never overlap - the simplest
 * form of backpressure: a slow Kafka broker naturally throttles the poll rate instead of
 * piling up concurrent batches.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final DecisionEventProducer decisionEventProducer;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxPublisher(OutboxRepository outboxRepository,
                            DecisionEventProducer decisionEventProducer,
                            @Value("${app.outbox.batch-size}") int batchSize,
                            @Value("${app.outbox.send-timeout-ms}") long sendTimeoutMs) {
        this.outboxRepository = outboxRepository;
        this.decisionEventProducer = decisionEventProducer;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> batch = outboxRepository.claimBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
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
        // Entities were loaded (and locked) inside this same transaction, so the field
        // mutations above are flushed to the DB automatically at commit - no explicit save().
    }
}
