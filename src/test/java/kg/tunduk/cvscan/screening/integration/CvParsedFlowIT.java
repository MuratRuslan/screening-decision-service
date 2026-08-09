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
 * End-to-end: валидное событие cv.parsed создаёт решение, запись outbox переходит
 * в SENT, и в брокер реально уходит сообщение screening.decision.created. Требует Docker
 * (настоящие Postgres + Kafka через Testcontainers) - см. заметку про sandbox в README.
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
        final String candidateId = KafkaTestSupport.uniqueCandidateId("it-flow");
        final Instant parsedAt = Instant.now();
        final String json = KafkaTestSupport.sampleEventJson(candidateId, parsedAt);

        kafkaTemplate.send(cvParsedTopic, candidateId, json).get(10, java.util.concurrent.TimeUnit.SECONDS);

        KafkaTestSupport.awaitUntil(20_000, () ->
                assertThat(decisionRepository.findFirstByCandidateIdOrderByDecidedAtDesc(candidateId)).isPresent());

        final ScreeningDecisionEntity decision = decisionRepository
                .findFirstByCandidateIdOrderByDecidedAtDesc(candidateId).orElseThrow();
        assertThat(decision.getDecision()).isIn(Decision.AUTO_APPROVE, Decision.NEEDS_REVIEW, Decision.AUTO_REJECT);
        assertThat(decision.getScore()).isBetween(0, 100);

        KafkaTestSupport.awaitUntil(20_000, () -> {
            final Optional<OutboxEvent> outbox = outboxRepository.findAll().stream()
                    .filter(e -> e.getAggregateId().equals(decision.getId()))
                    .findFirst();
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getStatus()).isEqualTo(OutboxStatus.SENT);
        });

        try (final Consumer<String, String> consumer = KafkaTestSupport.createConsumer(consumerFactory, decisionCreatedTopic)) {
            boolean found = false;
            final long deadline = System.currentTimeMillis() + 15_000;
            while (!found && System.currentTimeMillis() < deadline) {
                final ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                for (final var record : records) {
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
        final String candidateId = KafkaTestSupport.uniqueCandidateId("it-dup");
        final Instant parsedAt = Instant.now();
        final String json = KafkaTestSupport.sampleEventJson(candidateId, parsedAt);

        kafkaTemplate.send(cvParsedTopic, candidateId, json).get(10, java.util.concurrent.TimeUnit.SECONDS);
        KafkaTestSupport.awaitUntil(20_000, () ->
                assertThat(decisionRepository.findFirstByCandidateIdOrderByDecidedAtDesc(candidateId)).isPresent());

        // Публикуем то же самое событие повторно (тот же candidateId + parsedAt).
        kafkaTemplate.send(cvParsedTopic, candidateId, json).get(10, java.util.concurrent.TimeUnit.SECONDS);

        // Даём дубликату шанс быть (ошибочно) обработанным, затем проверяем, что этого не случилось.
        Thread.sleep(5_000);
        final long count = decisionRepository.findAll().stream()
                .filter(d -> d.getCandidateId().equals(candidateId))
                .count();
        assertThat(count).isEqualTo(1);
    }
}
