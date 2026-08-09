package kg.tunduk.cvscan.screening.semantic;

/** Ключ критерия из входящего события, для которого в каталоге нет алиаса/id. */
public record UnknownCriterion(String rawKey, String comment) {
}
