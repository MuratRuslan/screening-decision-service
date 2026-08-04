package kg.tunduk.test.senior.screeningdecisionservice.dto.rest;

import kg.tunduk.test.senior.screeningdecisionservice.model.SourceVerdict;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.Decision;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.RuleEvaluation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DecisionResponse(
        UUID id,
        String candidateId,
        Instant parsedAt,
        String name,
        String email,
        String position,
        SourceVerdict sourceVerdict,
        Decision decision,
        int score,
        String ruleSetVersion,
        List<RuleEvaluation> ruleResults,
        Instant decidedAt,
        int version,
        boolean overridden,
        String overrideReason
) {
}
