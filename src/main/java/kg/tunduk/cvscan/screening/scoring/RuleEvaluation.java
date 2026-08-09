package kg.tunduk.cvscan.screening.scoring;

public record RuleEvaluation(String key, RuleResult result, int points, String reason) {
}
