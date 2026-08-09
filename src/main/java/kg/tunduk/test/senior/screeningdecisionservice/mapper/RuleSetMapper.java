package kg.tunduk.test.senior.screeningdecisionservice.mapper;

import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.CriterionWeight;
import kg.tunduk.test.senior.screeningdecisionservice.generated.rest.model.RuleSetResponse;
import kg.tunduk.test.senior.screeningdecisionservice.model.RuleSetEntity;

import java.time.ZoneOffset;
import java.util.List;

public final class RuleSetMapper {

    private RuleSetMapper() {
    }

    public static RuleSetResponse toResponse(RuleSetEntity entity) {
        List<CriterionWeight> weights = entity.getWeights().stream()
                .map(w -> new CriterionWeight(w.key(), w.weight()))
                .toList();

        return new RuleSetResponse(
                entity.getPosition(),
                entity.getVersion(),
                entity.getActiveFrom().atOffset(ZoneOffset.UTC),
                entity.getMinApproveScore(),
                entity.getMaxRejectScore(),
                weights,
                entity.getId(),
                entity.getCreatedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
