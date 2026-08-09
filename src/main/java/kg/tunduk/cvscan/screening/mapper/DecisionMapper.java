package kg.tunduk.cvscan.screening.mapper;

import kg.tunduk.cvscan.screening.generated.rest.model.Decision;
import kg.tunduk.cvscan.screening.generated.rest.model.DecisionResponse;
import kg.tunduk.cvscan.screening.generated.rest.model.RuleEvaluation;
import kg.tunduk.cvscan.screening.generated.rest.model.RuleResult;
import kg.tunduk.cvscan.screening.generated.rest.model.SourceVerdict;
import kg.tunduk.cvscan.screening.model.ScreeningDecisionEntity;

import java.time.ZoneOffset;
import java.util.List;

public final class DecisionMapper {

    private DecisionMapper() {
    }

    public static DecisionResponse toResponse(ScreeningDecisionEntity entity) {
        List<RuleEvaluation> ruleResults = entity.getRuleResults().stream()
                .map(r -> new RuleEvaluation(r.key(), RuleResult.valueOf(r.result().name()), r.points(), r.reason()))
                .toList();

        DecisionResponse response = new DecisionResponse(
                entity.getId(),
                entity.getCandidateId(),
                entity.getParsedAt().atOffset(ZoneOffset.UTC),
                entity.getName(),
                entity.getEmail(),
                entity.getPosition(),
                SourceVerdict.valueOf(entity.getSourceVerdict().name()),
                Decision.valueOf(entity.getDecision().name()),
                entity.getScore(),
                entity.getRuleSetVersion(),
                ruleResults,
                entity.getDecidedAt().atOffset(ZoneOffset.UTC),
                entity.getVersion(),
                entity.isOverridden()
        );
        response.overrideReason(entity.getOverrideReason());
        return response;
    }
}
