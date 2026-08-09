package kg.tunduk.cvscan.screening.integration;

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

/** Общие хелперы для интеграционных тестов на Testcontainers - сам по себе не тестовый класс. */
final class KafkaTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KafkaTestSupport() {
    }

    static String uniqueCandidateId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /** Загружает java-senior/test-events/cv-parsed-sample.json и переписывает идентифицирующие поля. */
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
     * Строит raw-consumer из собственного автоконфигурированного бина Spring {@link ConsumerFactory},
     * переопределяя только group id. Это намеренно: самодельная карта свойств через
     * {@code @Value("${spring.kafka.bootstrap-servers}")} читает *статическое* значение по
     * умолчанию из application.yml (localhost:9092), а не то, что реально подключает
     * {@code @ServiceConnection} от Testcontainers - @ServiceConnection предоставляет бин
     * ConnectionDetails, который автоконфигурированные Kafka-бины используют напрямую, но не
     * переписывает свойство Environment. Использование автоконфигурированной ConsumerFactory
     * полностью обходит эту проблему, так как это та же фабрика, которой успешно пользуется
     * остальное приложение.
     */
    static Consumer<String, String> createConsumer(ConsumerFactory<String, String> consumerFactory, String topic) {
        Consumer<String, String> consumer = consumerFactory.createConsumer("it-consumer-" + UUID.randomUUID(), null);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    /** Опрашивает, пока {@code condition} не пройдёт или не истечёт {@code timeoutMs}, иначе падает с последней AssertionError. */
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
