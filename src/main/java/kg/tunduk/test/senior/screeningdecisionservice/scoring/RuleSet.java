package kg.tunduk.test.senior.screeningdecisionservice.scoring;

import java.time.Instant;
import java.util.List;

public record RuleSet(
        String position,
        String version,
        Instant activeFrom,
        int minApproveScore,
        int maxRejectScore,
        List<CriterionWeight> weights
) {
}
