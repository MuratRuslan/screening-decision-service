package kg.tunduk.cvscan.screening.semantic;

import kg.tunduk.cvscan.screening.scoring.NormalizedCriterion;

import java.util.List;
import java.util.Map;

public record NormalizationResult(Map<String, NormalizedCriterion> byCanonicalKey, List<UnknownCriterion> unmapped) {

    public boolean hasUnmapped() {
        return !unmapped.isEmpty();
    }
}
