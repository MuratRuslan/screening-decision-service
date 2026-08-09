package kg.tunduk.cvscan.screening.semantic;

import kg.tunduk.cvscan.screening.generated.kafka.Criterium;
import kg.tunduk.cvscan.screening.scoring.CriterionResult;
import kg.tunduk.cvscan.screening.scoring.NormalizedCriterion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Преобразует сырые значения {@code criteria[].key} входящего события в канонические id
 * каталога. Чистая Java - вызывающий сам решает, что делать с {@link NormalizationResult#unmapped()}
 * (см. {@link UnknownKeyPolicy}).
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
