package kg.tunduk.cvscan.screening.scoring;

import java.util.List;

public record ScoreOutcome(int score, Decision decision, List<RuleEvaluation> ruleResults) {
}
