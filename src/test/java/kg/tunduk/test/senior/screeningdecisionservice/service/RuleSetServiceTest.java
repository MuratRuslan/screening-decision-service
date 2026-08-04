package kg.tunduk.test.senior.screeningdecisionservice.service;

import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.CriterionWeightRequest;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.RuleSetRequest;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.RuleSetResponse;
import kg.tunduk.test.senior.screeningdecisionservice.exception.DuplicateRuleSetException;
import kg.tunduk.test.senior.screeningdecisionservice.exception.NotFoundException;
import kg.tunduk.test.senior.screeningdecisionservice.exception.RequestValidationException;
import kg.tunduk.test.senior.screeningdecisionservice.model.RuleSetEntity;
import kg.tunduk.test.senior.screeningdecisionservice.repository.RuleSetRepository;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.CriterionWeight;
import kg.tunduk.test.senior.screeningdecisionservice.semantic.CriteriaCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleSetServiceTest {

    private static CriteriaCatalog criteriaCatalog;

    @Mock
    private RuleSetRepository ruleSetRepository;

    @BeforeAll
    static void loadCatalog() throws Exception {
        try (InputStream in = RuleSetServiceTest.class.getClassLoader()
                .getResourceAsStream("semantic/criteria-catalog.json")) {
            criteriaCatalog = CriteriaCatalog.parse(in);
        }
    }

    private RuleSetService service() {
        return new RuleSetService(ruleSetRepository, criteriaCatalog);
    }

    private RuleSetRequest validRequest() {
        return new RuleSetRequest("java-senior", "v3", Instant.parse("2026-08-01T00:00:00Z"), 80, 45,
                List.of(new CriterionWeightRequest("java_spring", 25),
                        new CriterionWeightRequest("postgres_acid", 20),
                        new CriterionWeightRequest("kafka_reliability", 25),
                        new CriterionWeightRequest("contracts", 15),
                        new CriterionWeightRequest("observability", 15)));
    }

    @Test
    void returnsTheRuleSetWithGreatestActiveFromNotAfterNow() {
        RuleSetEntity entity = new RuleSetEntity(UUID.randomUUID(), "java-senior", "v2",
                Instant.parse("2026-07-01T00:00:00Z"), 75, 40,
                List.of(new CriterionWeight("java_spring", 20)), Instant.now());
        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class)))
                .thenReturn(Optional.of(entity));

        RuleSetResponse response = service().findActive("java-senior");

        assertThat(response.version()).isEqualTo("v2");
        assertThat(response.minApproveScore()).isEqualTo(75);
        verify(ruleSetRepository).findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class));
    }

    @Test
    void throwsNotFoundWhenNoRuleSetIsActiveYet() {
        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("unknown-position"), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().findActive("unknown-position"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createsRuleSetWhenPositionVersionPairIsNew() {
        when(ruleSetRepository.findByPositionAndVersion("java-senior", "v3")).thenReturn(Optional.empty());

        RuleSetResponse response = service().create(validRequest());

        assertThat(response.position()).isEqualTo("java-senior");
        assertThat(response.version()).isEqualTo("v3");
        assertThat(response.weights()).hasSize(5);
        verify(ruleSetRepository).save(any(RuleSetEntity.class));
    }

    @Test
    void rejectsDuplicatePositionVersionPair() {
        when(ruleSetRepository.findByPositionAndVersion("java-senior", "v3"))
                .thenReturn(Optional.of(new RuleSetEntity(UUID.randomUUID(), "java-senior", "v3",
                        Instant.now(), 80, 45, List.of(new CriterionWeight("java_spring", 100)), Instant.now())));

        assertThatThrownBy(() -> service().create(validRequest()))
                .isInstanceOf(DuplicateRuleSetException.class);
        verify(ruleSetRepository, never()).save(any());
    }

    @Test
    void rejectsWeightsReferencingUnknownCanonicalCriterion() {
        when(ruleSetRepository.findByPositionAndVersion("java-senior", "v3")).thenReturn(Optional.empty());
        RuleSetRequest request = new RuleSetRequest("java-senior", "v3", Instant.parse("2026-08-01T00:00:00Z"),
                80, 45, List.of(new CriterionWeightRequest("docker_kubernetes", 100)));

        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(ex -> assertThat(((RequestValidationException) ex).getDetails())
                        .anySatisfy(d -> assertThat(d.pointer()).isEqualTo("/weights/0/key")));
        verify(ruleSetRepository, never()).save(any());
    }

    @Test
    void rejectsAliasKeyEvenThoughItWouldResolveDuringNormalization() {
        when(ruleSetRepository.findByPositionAndVersion("java-senior", "v3")).thenReturn(Optional.empty());
        // "spring" is a valid alias for normalizing incoming events, but rule-sets must
        // reference the canonical id "java_spring" directly.
        RuleSetRequest request = new RuleSetRequest("java-senior", "v3", Instant.parse("2026-08-01T00:00:00Z"),
                80, 45, List.of(new CriterionWeightRequest("spring", 100)));

        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(RequestValidationException.class);
    }
}
