package kg.tunduk.cvscan.screening.scoring;

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
