package kg.tunduk.cvscan.screening.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kg.tunduk.cvscan.screening.TestcontainersConfiguration;
import kg.tunduk.cvscan.screening.repository.ScreeningDecisionRepository;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An invalid business event (fails JSON Schema) is routed to screening.decision.dlq with
 * JSON Pointer diagnostics, and does not stop the next valid message from being processed.
 * Requires Docker - see README's sandbox note.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class DlqIT {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private ScreeningDecisionRepository decisionRepository;

    @Value("${app.kafka.topics.cv-parsed}")
    private String cvParsedTopic;

    @Value("${app.kafka.topics.decision-dlq}")
    private String dlqTopic;

    @Test
    void invalidEventIsRoutedToDlqAndNextValidEventStillProcesses() throws Exception {
        String candidateId = KafkaTestSupport.uniqueCandidateId("it-invalid");
        String invalidJson = invalidPayload(candidateId);

        try (Consumer<String, String> dlqConsumer = KafkaTestSupport.createConsumer(consumerFactory, dlqTopic)) {
            kafkaTemplate.send(cvParsedTopic, candidateId, invalidJson).get(10, TimeUnit.SECONDS);

            boolean found = false;
            long deadline = System.currentTimeMillis() + 25_000;
            String lastSeenPayload = null;
            while (!found && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = dlqConsumer.poll(Duration.ofSeconds(2));
                for (var record : records) {
                    lastSeenPayload = record.value();
                    if (record.value() != null && record.value().contains(candidateId)) {
                        found = true;
                        assertThat(record.value()).contains("errorCode");
                        assertThat(record.value()).contains("pointer");
                        break;
                    }
                }
            }
            assertThat(found).as("DLQ message for %s, last seen: %s", candidateId, lastSeenPayload).isTrue();
        }

        // A subsequent valid event must still be processed normally - the invalid one must
        // not have stalled the partition.
        String validCandidateId = KafkaTestSupport.uniqueCandidateId("it-after-invalid");
        String validJson = KafkaTestSupport.sampleEventJson(validCandidateId, Instant.now());
        kafkaTemplate.send(cvParsedTopic, validCandidateId, validJson).get(10, TimeUnit.SECONDS);

        KafkaTestSupport.awaitUntil(20_000, () ->
                assertThat(decisionRepository.findFirstByCandidateIdOrderByDecidedAtDesc(validCandidateId)).isPresent());
    }

    /** Fails JSON Schema validation: name too short, email not a valid address. */
    private String invalidPayload(String candidateId) throws Exception {
        String raw = KafkaTestSupport.sampleEventJson(candidateId, Instant.now());
        JsonNode node = KafkaTestSupport.readJson(raw);
        ObjectNode obj = (ObjectNode) node;
        obj.put("name", "x");
        obj.put("email", "not-an-email");
        return obj.toString();
    }
}
