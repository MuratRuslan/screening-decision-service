package kg.tunduk.cvscan.screening.semantic;

import kg.tunduk.cvscan.screening.generated.kafka.Criterium;
import kg.tunduk.cvscan.screening.scoring.CriterionResult;
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
        try (final InputStream in = SemanticNormalizerTest.class.getClassLoader()
                .getResourceAsStream("semantic/criteria-catalog.json")) {
            catalog = CriteriaCatalog.parse(in);
        }
    }

    private static Criterium criterium(final String key, final String result, final String comment) {
        final Criterium item = new Criterium();
        item.setKey(key);
        item.setResult(Criterium.Result.valueOf(result));
        item.setComment(comment);
        return item;
    }

    @Test
    void resolvesExactCanonicalKey() {
        final NormalizationResult result = SemanticNormalizer.normalize(catalog,
                List.of(criterium("java_spring", "OK", "7 лет")));

        assertThat(result.byCanonicalKey()).containsKey("java_spring");
        assertThat(result.byCanonicalKey().get("java_spring").result()).isEqualTo(CriterionResult.OK);
        assertThat(result.unmapped()).isEmpty();
    }

    @Test
    void resolvesAliasToCanonicalKey() {
        final NormalizationResult result = SemanticNormalizer.normalize(catalog,
                List.of(criterium("spring_boot", "PARTIAL", "базовый Spring Boot")));

        assertThat(result.byCanonicalKey()).containsKey("java_spring");
        assertThat(result.byCanonicalKey().get("java_spring").canonicalKey()).isEqualTo("java_spring");
        assertThat(result.unmapped()).isEmpty();
    }

    @Test
    void resolutionIsCaseInsensitive() {
        final NormalizationResult result = SemanticNormalizer.normalize(catalog,
                List.of(criterium("KAFKA", "OK", "продакшн Kafka")));

        assertThat(result.byCanonicalKey()).containsKey("kafka_reliability");
    }

    @Test
    void unknownKeyIsNeverSilentlyDropped() {
        final NormalizationResult result = SemanticNormalizer.normalize(catalog,
                List.of(criterium("docker_kubernetes", "OK", "K8s опыт")));

        assertThat(result.byCanonicalKey()).isEmpty();
        assertThat(result.hasUnmapped()).isTrue();
        assertThat(result.unmapped()).containsExactly(new UnknownCriterion("docker_kubernetes", "K8s опыт"));
    }

    @Test
    void normalizesMultipleCriteriaTogether() {
        final NormalizationResult result = SemanticNormalizer.normalize(catalog, List.of(
                criterium("java", "OK", "Java"),
                criterium("postgresql", "PARTIAL", "Postgres"),
                criterium("dlq", "OK", "DLQ"),
                criterium("xsd", "NO", "нет SOAP"),
                criterium("grafana", "OK", "Grafana"),
                criterium("unknown_thing", "OK", "???")
        ));

        assertThat(result.byCanonicalKey().keySet())
                .containsExactlyInAnyOrder("java_spring", "postgres_acid", "kafka_reliability", "contracts", "observability");
        assertThat(result.unmapped()).extracting(UnknownCriterion::rawKey).containsExactly("unknown_thing");
    }
}
