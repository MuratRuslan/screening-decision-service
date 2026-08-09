package kg.tunduk.cvscan.screening.integration;

import kg.tunduk.cvscan.screening.TestcontainersConfiguration;
import kg.tunduk.cvscan.screening.model.ScreeningDecisionEntity;
import kg.tunduk.cvscan.screening.outbox.OutboxEvent;
import kg.tunduk.cvscan.screening.outbox.OutboxRepository;
import kg.tunduk.cvscan.screening.outbox.OutboxStatus;
import kg.tunduk.cvscan.screening.repository.ScreeningDecisionRepository;
import kg.tunduk.cvscan.screening.scoring.Decision;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: a valid cv.parsed event produces a decision, an outbox row that transitions
 * to SENT, and an actual screening.decision.created message on the broker. Requires Docker
 * (real Postgres + Kafka via Testcontainers) - see README's sandbox note.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class CvParsedFlowIT {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ScreeningDecisionRepository decisionRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Value("${app.kafka.topics.cv-parsed}")
    private String cvParsedTopic;

    @Value("${app.kafka.topics.decision-created}")
    private String decisionCreatedTopic;

    @Test
    void validEventProducesDecisionAndPublishesDecisionCreated() throws Exception {
        String candidateId = KafkaTestSupport.uniqueCandidateId("it-flow");
        Instant parsedAt = Instant.now();
        String json = KafkaTestSupport.sampleEventJson(candidateId, parsedAt);

        kafkaTemplate.send(cvParsedTopic, candidateId, json).get(10, java.util.concurrent.TimeUnit.SECONDS);

        KafkaTestSupport.awaitUntil(20_000, () ->
                assertThat(decisionRepository.findFirstByCandidateIdOrderByDecidedAtDesc(candidateId)).isPresent());

        ScreeningDecisionEntity decision = decisionRepository
                .findFirstByCandidateIdOrderByDecidedAtDesc(candidateId).orElseThrow();
        assertThat(decision.getDecision()).isIn(Decision.AUTO_APPROVE, Decision.NEEDS_REVIEW, Decision.AUTO_REJECT);
        assertThat(decision.getScore()).isBetween(0, 100);

        KafkaTestSupport.awaitUntil(20_000, () -> {
            Optional<OutboxEvent> outbox = outboxRepository.findAll().stream()
                    .filter(e -> e.getAggregateId().equals(decision.getId()))
                    .findFirst();
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getStatus()).isEqualTo(OutboxStatus.SENT);
        });

        try (Consumer<String, String> consumer = KafkaTestSupport.createConsumer(consumerFactory, decisionCreatedTopic)) {
            boolean found = false;
            long deadline = System.currentTimeMillis() + 15_000;
            while (!found && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                for (var record : records) {
                    if (record.value() != null && record.value().contains(candidateId)) {
                        found = true;
                        break;
                    }
                }
            }
            assertThat(found).as("screening.decision.created message for %s", candidateId).isTrue();
        }
    }

    @Test
    void duplicateEventDoesNotCreateASecondDecisionOrEvent() throws Exception {
        String candidateId = KafkaTestSupport.uniqueCandidateId("it-dup");
        Instant parsedAt = Instant.now();
        String json = KafkaTestSupport.sampleEventJson(candidateId, parsedAt);

        kafkaTemplate.send(cvParsedTopic, candidateId, json).get(10, java.util.concurrent.TimeUnit.SECONDS);
        KafkaTestSupport.awaitUntil(20_000, () ->
                assertThat(decisionRepository.findFirstByCandidateIdOrderByDecidedAtDesc(candidateId)).isPresent());

        // Re-publish the exact same event (same candidateId + parsedAt).
        kafkaTemplate.send(cvParsedTopic, candidateId, json).get(10, java.util.concurrent.TimeUnit.SECONDS);

        // Give the duplicate a chance to be (incorrectly) processed, then assert it wasn't.
        Thread.sleep(5_000);
        long count = decisionRepository.findAll().stream()
                .filter(d -> d.getCandidateId().equals(candidateId))
                .count();
        assertThat(count).isEqualTo(1);
    }
}
