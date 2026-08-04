package kg.tunduk.test.senior.screeningdecisionservice.semantic;

import kg.tunduk.test.senior.screeningdecisionservice.dto.kafka.CriteriaItemDto;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.CriterionResult;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.NormalizedCriterion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves raw {@code criteria[].key} values from an incoming event to canonical catalog
 * ids. Pure Java - the caller decides what to do with {@link NormalizationResult#unmapped()}
 * (see {@link UnknownKeyPolicy}).
 */
public final class SemanticNormalizer {

    private SemanticNormalizer() {
    }

    public static NormalizationResult normalize(CriteriaCatalog catalog, List<CriteriaItemDto> rawCriteria) {
        Map<String, NormalizedCriterion> byCanonicalKey = new LinkedHashMap<>();
        List<UnknownCriterion> unmapped = new ArrayList<>();

        for (CriteriaItemDto item : rawCriteria) {
            Optional<String> canonicalKey = catalog.resolve(item.key());
            if (canonicalKey.isPresent()) {
                String key = canonicalKey.get();
                CriterionResult result = CriterionResult.valueOf(item.result());
                byCanonicalKey.put(key, new NormalizedCriterion(key, result, item.comment()));
            } else {
                unmapped.add(new UnknownCriterion(item.key(), item.comment()));
            }
        }

        return new NormalizationResult(Map.copyOf(byCanonicalKey), List.copyOf(unmapped));
    }
}
