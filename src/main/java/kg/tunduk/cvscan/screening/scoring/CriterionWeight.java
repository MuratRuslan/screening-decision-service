package kg.tunduk.cvscan.screening.scoring;

/**
 * {@code key} is always a canonical criterion id (already normalized through the
 * semantic catalog) — the scoring engine never sees raw/alias keys.
 */
public record CriterionWeight(String key, int weight) {
}
