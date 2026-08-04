package kg.tunduk.test.senior.screeningdecisionservice.mapper;

import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.RuleSetResponse;
import kg.tunduk.test.senior.screeningdecisionservice.model.RuleSetEntity;

public final class RuleSetMapper {

    private RuleSetMapper() {
    }

    public static RuleSetResponse toResponse(RuleSetEntity entity) {
        return new RuleSetResponse(
                entity.getId(),
                entity.getPosition(),
                entity.getVersion(),
                entity.getActiveFrom(),
                entity.getMinApproveScore(),
                entity.getMaxRejectScore(),
                entity.getWeights(),
                entity.getCreatedAt()
        );
    }
}
