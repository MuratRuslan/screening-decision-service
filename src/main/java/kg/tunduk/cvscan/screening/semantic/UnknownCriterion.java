package kg.tunduk.cvscan.screening.semantic;

/** A criterion key from the incoming event that the catalog has no alias/id for. */
public record UnknownCriterion(String rawKey, String comment) {
}
