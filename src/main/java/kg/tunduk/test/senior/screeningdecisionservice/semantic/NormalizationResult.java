package kg.tunduk.test.senior.screeningdecisionservice.semantic;

import kg.tunduk.test.senior.screeningdecisionservice.scoring.NormalizedCriterion;

import java.util.List;
import java.util.Map;

public record NormalizationResult(Map<String, NormalizedCriterion> byCanonicalKey, List<UnknownCriterion> unmapped) {

    public boolean hasUnmapped() {
        return !unmapped.isEmpty();
    }
}
