package kg.tunduk.test.senior.screeningdecisionservice.service;

import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.RuleSetResponse;
import kg.tunduk.test.senior.screeningdecisionservice.exception.NotFoundException;
import kg.tunduk.test.senior.screeningdecisionservice.model.RuleSetEntity;
import kg.tunduk.test.senior.screeningdecisionservice.repository.RuleSetRepository;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.CriterionWeight;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleSetServiceTest {

    @Mock
    private RuleSetRepository ruleSetRepository;

    @Test
    void returnsTheRuleSetWithGreatestActiveFromNotAfterNow() {
        RuleSetEntity entity = new RuleSetEntity(UUID.randomUUID(), "java-senior", "v2",
                Instant.parse("2026-07-01T00:00:00Z"), 75, 40,
                List.of(new CriterionWeight("java_spring", 20)), Instant.now());
        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class)))
                .thenReturn(Optional.of(entity));

        RuleSetService service = new RuleSetService(ruleSetRepository);
        RuleSetResponse response = service.findActive("java-senior");

        assertThat(response.version()).isEqualTo("v2");
        assertThat(response.minApproveScore()).isEqualTo(75);
        verify(ruleSetRepository).findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("java-senior"), any(Instant.class));
    }

    @Test
    void throwsNotFoundWhenNoRuleSetIsActiveYet() {
        when(ruleSetRepository.findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(eq("unknown-position"), any(Instant.class)))
                .thenReturn(Optional.empty());

        RuleSetService service = new RuleSetService(ruleSetRepository);

        assertThatThrownBy(() -> service.findActive("unknown-position"))
                .isInstanceOf(NotFoundException.class);
    }
}
