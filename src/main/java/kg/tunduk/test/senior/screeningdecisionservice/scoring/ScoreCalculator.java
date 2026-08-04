package kg.tunduk.test.senior.screeningdecisionservice.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure, side-effect-free scoring engine. Deliberately has zero Spring/JPA imports so it
 * can be unit-tested without a Spring context.
 */
public final class ScoreCalculator {

    private static final String MISSING_CRITERION_REASON =
            "Критерий отсутствует во входящем событии, учтён как NO";

    private ScoreCalculator() {
    }

    /**
     * @param byCanonicalKey criteria from the incoming event, already normalized to
     *                       canonical keys via the semantic catalog. A rule-set criterion
     *                       with no matching entry is treated as {@link CriterionResult#NO}.
     */
    public static ScoreOutcome calculate(RuleSet ruleSet, Map<String, NormalizedCriterion> byCanonicalKey) {
        List<RuleEvaluation> evaluations = new ArrayList<>(ruleSet.weights().size());
        double totalContribution = 0.0;

        for (CriterionWeight weight : ruleSet.weights()) {
            NormalizedCriterion criterion = byCanonicalKey.get(weight.key());
            CriterionResult result = criterion != null ? criterion.result() : CriterionResult.NO;
            double contribution = weight.weight() * percentageFor(result);
            totalContribution += contribution;

            String reason = criterion != null
                    ? reasonFor(result, criterion.comment())
                    : MISSING_CRITERION_REASON;
            evaluations.add(new RuleEvaluation(weight.key(), ruleResultFor(result), (int) Math.floor(contribution), reason));
        }

        int score = clamp((int) Math.floor(totalContribution));
        Decision decision = decide(ruleSet, score);
        return new ScoreOutcome(score, decision, List.copyOf(evaluations));
    }

    private static double percentageFor(CriterionResult result) {
        return switch (result) {
            case OK -> 1.0;
            case PARTIAL -> 0.5;
            case NO -> 0.0;
        };
    }

    private static RuleResult ruleResultFor(CriterionResult result) {
        return switch (result) {
            case OK -> RuleResult.PASS;
            case PARTIAL -> RuleResult.WARN;
            case NO -> RuleResult.FAIL;
        };
    }

    private static String reasonFor(CriterionResult result, String comment) {
        if (comment == null || comment.isBlank()) {
            return result.name();
        }
        return result.name() + ": " + comment;
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static Decision decide(RuleSet ruleSet, int score) {
        if (score >= ruleSet.minApproveScore()) {
            return Decision.AUTO_APPROVE;
        }
        if (score <= ruleSet.maxRejectScore()) {
            return Decision.AUTO_REJECT;
        }
        return Decision.NEEDS_REVIEW;
    }
}
