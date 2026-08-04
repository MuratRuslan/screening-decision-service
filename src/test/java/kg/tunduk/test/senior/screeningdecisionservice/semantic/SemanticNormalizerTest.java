package kg.tunduk.test.senior.screeningdecisionservice.semantic;

import kg.tunduk.test.senior.screeningdecisionservice.dto.kafka.CriteriaItemDto;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.CriterionResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticNormalizerTest {

    private static CriteriaCatalog catalog;

    @BeforeAll
    static void loadCatalog() throws IOException {
        try (InputStream in = SemanticNormalizerTest.class.getClassLoader()
                .getResourceAsStream("semantic/criteria-catalog.json")) {
            catalog = CriteriaCatalog.parse(in);
        }
    }

    @Test
    void resolvesExactCanonicalKey() {
        NormalizationResult result = SemanticNormalizer.normalize(catalog,
                List.of(new CriteriaItemDto("java_spring", "OK", "7 лет")));

        assertThat(result.byCanonicalKey()).containsKey("java_spring");
        assertThat(result.byCanonicalKey().get("java_spring").result()).isEqualTo(CriterionResult.OK);
        assertThat(result.unmapped()).isEmpty();
    }

    @Test
    void resolvesAliasToCanonicalKey() {
        NormalizationResult result = SemanticNormalizer.normalize(catalog,
                List.of(new CriteriaItemDto("spring_boot", "PARTIAL", "базовый Spring Boot")));

        assertThat(result.byCanonicalKey()).containsKey("java_spring");
        assertThat(result.byCanonicalKey().get("java_spring").canonicalKey()).isEqualTo("java_spring");
        assertThat(result.unmapped()).isEmpty();
    }

    @Test
    void resolutionIsCaseInsensitive() {
        NormalizationResult result = SemanticNormalizer.normalize(catalog,
                List.of(new CriteriaItemDto("KAFKA", "OK", "продакшн Kafka")));

        assertThat(result.byCanonicalKey()).containsKey("kafka_reliability");
    }

    @Test
    void unknownKeyIsNeverSilentlyDropped() {
        NormalizationResult result = SemanticNormalizer.normalize(catalog,
                List.of(new CriteriaItemDto("docker_kubernetes", "OK", "K8s опыт")));

        assertThat(result.byCanonicalKey()).isEmpty();
        assertThat(result.hasUnmapped()).isTrue();
        assertThat(result.unmapped()).containsExactly(new UnknownCriterion("docker_kubernetes", "K8s опыт"));
    }

    @Test
    void normalizesMultipleCriteriaTogether() {
        NormalizationResult result = SemanticNormalizer.normalize(catalog, List.of(
                new CriteriaItemDto("java", "OK", "Java"),
                new CriteriaItemDto("postgresql", "PARTIAL", "Postgres"),
                new CriteriaItemDto("dlq", "OK", "DLQ"),
                new CriteriaItemDto("xsd", "NO", "нет SOAP"),
                new CriteriaItemDto("grafana", "OK", "Grafana"),
                new CriteriaItemDto("unknown_thing", "OK", "???")
        ));

        assertThat(result.byCanonicalKey().keySet())
                .containsExactlyInAnyOrder("java_spring", "postgres_acid", "kafka_reliability", "contracts", "observability");
        assertThat(result.unmapped()).extracting(UnknownCriterion::rawKey).containsExactly("unknown_thing");
    }
}
