package kg.tunduk.test.senior.screeningdecisionservice.mapper;

import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.DecisionResponse;
import kg.tunduk.test.senior.screeningdecisionservice.model.ScreeningDecisionEntity;

public final class DecisionMapper {

    private DecisionMapper() {
    }

    public static DecisionResponse toResponse(ScreeningDecisionEntity entity) {
        return new DecisionResponse(
                entity.getId(),
                entity.getCandidateId(),
                entity.getParsedAt(),
                entity.getName(),
                entity.getEmail(),
                entity.getPosition(),
                entity.getSourceVerdict(),
                entity.getDecision(),
                entity.getScore(),
                entity.getRuleSetVersion(),
                entity.getRuleResults(),
                entity.getDecidedAt(),
                entity.getVersion(),
                entity.isOverridden(),
                entity.getOverrideReason()
        );
    }
}
