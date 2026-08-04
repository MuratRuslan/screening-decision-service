package kg.tunduk.test.senior.screeningdecisionservice.scoring;

import java.util.List;

public record ScoreOutcome(int score, Decision decision, List<RuleEvaluation> ruleResults) {
}
