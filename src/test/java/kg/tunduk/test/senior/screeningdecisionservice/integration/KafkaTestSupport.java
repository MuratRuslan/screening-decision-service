package kg.tunduk.test.senior.screeningdecisionservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    static Consumer<String, String> createConsumer(String bootstrapServers, String topic) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer();
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
