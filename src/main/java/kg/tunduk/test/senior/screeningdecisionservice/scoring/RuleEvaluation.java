package kg.tunduk.test.senior.screeningdecisionservice.scoring;

public record RuleEvaluation(String key, RuleResult result, int points, String reason) {
}
