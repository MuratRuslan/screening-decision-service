package kg.tunduk.test.senior.screeningdecisionservice.dto.rest;

import kg.tunduk.test.senior.screeningdecisionservice.scoring.CriterionWeight;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RuleSetResponse(
        UUID id,
        String position,
        String version,
        Instant activeFrom,
        int minApproveScore,
        int maxRejectScore,
        List<CriterionWeight> weights,
        Instant createdAt
) {
}
