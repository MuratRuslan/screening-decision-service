package kg.tunduk.test.senior.screeningdecisionservice.semantic;

import kg.tunduk.test.senior.screeningdecisionservice.generated.kafka.Criterium;
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

    public static NormalizationResult normalize(CriteriaCatalog catalog, List<Criterium> rawCriteria) {
        Map<String, NormalizedCriterion> byCanonicalKey = new LinkedHashMap<>();
        List<UnknownCriterion> unmapped = new ArrayList<>();

        for (Criterium item : rawCriteria) {
            Optional<String> canonicalKey = catalog.resolve(item.getKey());
            if (canonicalKey.isPresent()) {
                String key = canonicalKey.get();
                CriterionResult result = CriterionResult.valueOf(item.getResult().name());
                byCanonicalKey.put(key, new NormalizedCriterion(key, result, item.getComment()));
            } else {
                unmapped.add(new UnknownCriterion(item.getKey(), item.getComment()));
            }
        }

        return new NormalizationResult(Map.copyOf(byCanonicalKey), List.copyOf(unmapped));
    }
}
