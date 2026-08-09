package kg.tunduk.test.senior.screeningdecisionservice.service;

import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.Decision;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.DecisionOverrideRequest;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.DecisionResponse;
import kg.tunduk.test.senior.screeningdecisionservice.exception.NotFoundException;
import kg.tunduk.test.senior.screeningdecisionservice.exception.VersionConflictException;
import kg.tunduk.test.senior.screeningdecisionservice.model.DecisionAuditEntity;
import kg.tunduk.test.senior.screeningdecisionservice.model.ScreeningDecisionEntity;
import kg.tunduk.test.senior.screeningdecisionservice.model.SourceVerdict;
import kg.tunduk.test.senior.screeningdecisionservice.repository.DecisionAuditRepository;
import kg.tunduk.test.senior.screeningdecisionservice.repository.ScreeningDecisionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionOverrideServiceTest {

    @Mock
    private ScreeningDecisionRepository decisionRepository;

    @Mock
    private DecisionAuditRepository auditRepository;

    private DecisionOverrideService service() {
        return new DecisionOverrideService(decisionRepository, auditRepository);
    }

    /** ScreeningDecisionEntity initializes {@code version = 1} for a never-yet-overridden decision. */
    private ScreeningDecisionEntity newDecision() {
        return new ScreeningDecisionEntity(UUID.randomUUID(), "senior-test",
                Instant.parse("2026-06-01T00:00:00Z"), "Тест Тестов", "test@example.com", "java-senior",
                SourceVerdict.PARTIAL, kg.tunduk.test.senior.screeningdecisionservice.scoring.Decision.NEEDS_REVIEW,
                60, "v1", List.of(), "2026.06", Instant.now());
    }

    @Test
    void matchingExpectedVersionAppliesOverrideAndWritesAudit() {
        ScreeningDecisionEntity decision = newDecision();
        when(decisionRepository.findById(decision.getId())).thenReturn(Optional.of(decision));

        DecisionOverrideRequest request = new DecisionOverrideRequest(Decision.AUTO_APPROVE,
                "Техническое интервью подтвердило уровень выше автооценки");
        DecisionResponse response = service().override(decision.getId(), 1, request);

        assertThat(response.getDecision()).isEqualTo(Decision.AUTO_APPROVE);
        assertThat(response.getOverridden()).isTrue();
        assertThat(response.getOverrideReason().get()).isEqualTo(request.getReason());

        ArgumentCaptor<DecisionAuditEntity> auditCaptor = ArgumentCaptor.forClass(DecisionAuditEntity.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getDecisionId()).isEqualTo(decision.getId());
        assertThat(auditCaptor.getValue().getPayload()).containsEntry("previousDecision", "NEEDS_REVIEW");
        assertThat(auditCaptor.getValue().getPayload()).containsEntry("newDecision", "AUTO_APPROVE");
    }

    @Test
    void staleExpectedVersionIsRejectedWithoutMutatingOrAuditing() {
        ScreeningDecisionEntity decision = newDecision();
        when(decisionRepository.findById(decision.getId())).thenReturn(Optional.of(decision));

        DecisionOverrideRequest request = new DecisionOverrideRequest(Decision.AUTO_APPROVE, "some reason text");

        assertThatThrownBy(() -> service().override(decision.getId(), 2, request))
                .isInstanceOf(VersionConflictException.class)
                .hasMessageContaining("2")
                .hasMessageContaining("1");

        assertThat(decision.isOverridden()).isFalse();
        verify(auditRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownDecisionIdIsNotFound() {
        UUID id = UUID.randomUUID();
        when(decisionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().override(id, 1, new DecisionOverrideRequest(Decision.AUTO_REJECT, "some reason text")))
                .isInstanceOf(NotFoundException.class);
    }
}
