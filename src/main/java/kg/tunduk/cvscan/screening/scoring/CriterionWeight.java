package kg.tunduk.cvscan.screening.scoring;

/**
 * {@code key} всегда является каноническим id критерия (уже нормализован через
 * семантический каталог) — движок скоринга никогда не видит сырые/алиасные ключи.
 */
public record CriterionWeight(String key, int weight) {
}
