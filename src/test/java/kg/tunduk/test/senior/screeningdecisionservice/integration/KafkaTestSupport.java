package kg.tunduk.test.senior.screeningdecisionservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.kafka.core.ConsumerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Shared helpers for Testcontainers-backed integration tests - not itself a test class. */
final class KafkaTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KafkaTestSupport() {
    }

    static String uniqueCandidateId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /** Loads java-senior/test-events/cv-parsed-sample.json and rewrites the identifying fields. */
    static String sampleEventJson(String candidateId, Instant parsedAt) throws Exception {
        String raw = Files.readString(Path.of("java-senior/test-events/cv-parsed-sample.json"));
        ObjectNode node = (ObjectNode) MAPPER.readTree(raw);
        node.put("eventId", UUID.randomUUID().toString());
        node.put("candidateId", candidateId);
        node.put("parsedAt", parsedAt.toString());
        return MAPPER.writeValueAsString(node);
    }

    static JsonNode readJson(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    /**
     * Builds a raw consumer from Spring's own autoconfigured {@link ConsumerFactory} bean,
     * only overriding the group id. This is deliberate: a hand-built props map using
     * {@code @Value("${spring.kafka.bootstrap-servers}")} reads the *static*
     * application.yml default (localhost:9092) rather than the value Testcontainers'
     * {@code @ServiceConnection} actually wires up - @ServiceConnection supplies a
     * ConnectionDetails bean that autoconfigured Kafka beans consume directly, it does not
     * rewrite the Environment property. Going through the autoconfigured ConsumerFactory
     * sidesteps that entirely, since it's the same factory the rest of the app already uses
     * successfully.
     */
    static Consumer<String, String> createConsumer(ConsumerFactory<String, String> consumerFactory, String topic) {
        Consumer<String, String> consumer = consumerFactory.createConsumer("it-consumer-" + UUID.randomUUID(), null);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    /** Polls until {@code condition} passes or {@code timeoutMs} elapses, else fails via the last AssertionError. */
    static void awaitUntil(long timeoutMs, Runnable assertion) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        AssertionError lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                assertion.run();
                return;
            } catch (AssertionError e) {
                lastFailure = e;
                Thread.sleep(200);
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
    }
}
