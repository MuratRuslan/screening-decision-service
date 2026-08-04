package kg.tunduk.test.senior.screeningdecisionservice.scoring;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCalculatorTest {

    private static final RuleSet JAVA_SENIOR_V1 = new RuleSet(
            "java-senior", "v1", Instant.parse("2026-06-04T00:00:00Z"), 80, 45,
            List.of(
                    new CriterionWeight("java_spring", 25),
                    new CriterionWeight("postgres_acid", 20),
                    new CriterionWeight("kafka_reliability", 25),
                    new CriterionWeight("contracts", 15),
                    new CriterionWeight("observability", 15)
            ));

    @Test
    void allOkYieldsFullScoreAndAutoApprove() {
        Map<String, NormalizedCriterion> criteria = Map.of(
                "java_spring", ok("java_spring", "7 лет"),
                "postgres_acid", ok("postgres_acid", "ACID"),
                "kafka_reliability", ok("kafka_reliability", "DLQ"),
                "contracts", ok("contracts", "OpenAPI"),
                "observability", ok("observability", "Prometheus")
        );

        ScoreOutcome outcome = ScoreCalculator.calculate(JAVA_SENIOR_V1, criteria);

        assertThat(outcome.score()).isEqualTo(100);
        assertThat(outcome.decision()).isEqualTo(Decision.AUTO_APPROVE);
        assertThat(outcome.ruleResults()).allSatisfy(r -> assertThat(r.result()).isEqualTo(RuleResult.PASS));
    }

    @Test
    void allNoYieldsZeroScoreAndAutoReject() {
        Map<String, NormalizedCriterion> criteria = Map.of(
                "java_spring", no("java_spring", "нет опыта"),
                "postgres_acid", no("postgres_acid", "нет опыта"),
                "kafka_reliability", no("kafka_reliability", "нет опыта"),
                "contracts", no("contracts", "нет опыта"),
                "observability", no("observability", "нет опыта")
        );

        ScoreOutcome outcome = ScoreCalculator.calculate(JAVA_SENIOR_V1, criteria);

        assertThat(outcome.score()).isEqualTo(0);
        assertThat(outcome.decision()).isEqualTo(Decision.AUTO_REJECT);
        assertThat(outcome.ruleResults()).allSatisfy(r -> {
            assertThat(r.result()).isEqualTo(RuleResult.FAIL);
            assertThat(r.points()).isZero();
        });
    }

    @Test
    void allPartialYieldsHalfWeightScoreAndNeedsReview() {
        Map<String, NormalizedCriterion> criteria = Map.of(
                "java_spring", partial("java_spring", "middle+"),
                "postgres_acid", partial("postgres_acid", "базовый SQL"),
                "kafka_reliability", partial("kafka_reliability", "без DLQ"),
                "contracts", partial("contracts", "без JSON Schema"),
                "observability", partial("observability", "только логи")
        );

        ScoreOutcome outcome = ScoreCalculator.calculate(JAVA_SENIOR_V1, criteria);

        // 25*0.5 + 20*0.5 + 25*0.5 + 15*0.5 + 15*0.5 = 50.0, floored once
        assertThat(outcome.score()).isEqualTo(50);
        assertThat(outcome.decision()).isEqualTo(Decision.NEEDS_REVIEW);
        assertThat(outcome.ruleResults()).allSatisfy(r -> assertThat(r.result()).isEqualTo(RuleResult.WARN));
    }

    @Test
    void missingCriterionIsTreatedAsNo() {
        Map<String, NormalizedCriterion> criteria = Map.of(
                "java_spring", ok("java_spring", "7 лет"),
                "postgres_acid", ok("postgres_acid", "ACID"),
                "kafka_reliability", ok("kafka_reliability", "DLQ")
                // "contracts" and "observability" absent from the incoming event
        );

        ScoreOutcome outcome = ScoreCalculator.calculate(JAVA_SENIOR_V1, criteria);

        // 25 + 20 + 25 + 0 + 0 = 70
        assertThat(outcome.score()).isEqualTo(70);
        assertThat(outcome.decision()).isEqualTo(Decision.NEEDS_REVIEW);

        RuleEvaluation contracts = findByKey(outcome, "contracts");
        assertThat(contracts.result()).isEqualTo(RuleResult.FAIL);
        assertThat(contracts.points()).isZero();
        assertThat(contracts.reason()).contains("отсутствует");
    }

    @Test
    void minApproveScoreBoundaryIsInclusive() {
        RuleSet boundaryRuleSet = new RuleSet("x", "v1", Instant.EPOCH, 80, 45,
                List.of(new CriterionWeight("a", 80), new CriterionWeight("b", 20)));
        Map<String, NormalizedCriterion> criteria = Map.of(
                "a", ok("a", "full"),
                "b", no("b", "none")
        );

        ScoreOutcome outcome = ScoreCalculator.calculate(boundaryRuleSet, criteria);

        assertThat(outcome.score()).isEqualTo(80);
        assertThat(outcome.decision()).isEqualTo(Decision.AUTO_APPROVE);
    }

    @Test
    void maxRejectScoreBoundaryIsInclusive() {
        RuleSet boundaryRuleSet = new RuleSet("x", "v1", Instant.EPOCH, 80, 45,
                List.of(new CriterionWeight("a", 45), new CriterionWeight("b", 55)));
        Map<String, NormalizedCriterion> criteria = Map.of(
                "a", ok("a", "full"),
                "b", no("b", "none")
        );

        ScoreOutcome outcome = ScoreCalculator.calculate(boundaryRuleSet, criteria);

        assertThat(outcome.score()).isEqualTo(45);
        assertThat(outcome.decision()).isEqualTo(Decision.AUTO_REJECT);
    }

    @Test
    void scoreJustAboveMaxRejectAndBelowMinApproveIsNeedsReview() {
        RuleSet ruleSet = new RuleSet("x", "v1", Instant.EPOCH, 80, 45,
                List.of(new CriterionWeight("a", 46), new CriterionWeight("b", 54)));
        Map<String, NormalizedCriterion> criteria = Map.of(
                "a", ok("a", "full"),
                "b", no("b", "none")
        );

        ScoreOutcome outcome = ScoreCalculator.calculate(ruleSet, criteria);

        assertThat(outcome.score()).isEqualTo(46);
        assertThat(outcome.decision()).isEqualTo(Decision.NEEDS_REVIEW);
    }

    @Test
    void totalWeightAboveHundredIsClampedToHundred() {
        RuleSet overweightRuleSet = new RuleSet("x", "v1", Instant.EPOCH, 80, 45,
                List.of(new CriterionWeight("a", 80), new CriterionWeight("b", 80)));
        Map<String, NormalizedCriterion> criteria = Map.of(
                "a", ok("a", "full"),
                "b", ok("b", "full")
        );

        ScoreOutcome outcome = ScoreCalculator.calculate(overweightRuleSet, criteria);

        assertThat(outcome.score()).isEqualTo(100);
        assertThat(outcome.decision()).isEqualTo(Decision.AUTO_APPROVE);
    }

    @Test
    void ruleResultsPreserveRuleSetWeightOrder() {
        Map<String, NormalizedCriterion> criteria = Map.of(
                "java_spring", ok("java_spring", "x"),
                "postgres_acid", ok("postgres_acid", "x"),
                "kafka_reliability", ok("kafka_reliability", "x"),
                "contracts", ok("contracts", "x"),
                "observability", ok("observability", "x")
        );

        ScoreOutcome outcome = ScoreCalculator.calculate(JAVA_SENIOR_V1, criteria);

        assertThat(outcome.ruleResults())
                .extracting(RuleEvaluation::key)
                .containsExactly("java_spring", "postgres_acid", "kafka_reliability", "contracts", "observability");
    }

    private static RuleEvaluation findByKey(ScoreOutcome outcome, String key) {
        return outcome.ruleResults().stream()
                .filter(r -> r.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private static NormalizedCriterion ok(String key, String comment) {
        return new NormalizedCriterion(key, CriterionResult.OK, comment);
    }

    private static NormalizedCriterion partial(String key, String comment) {
        return new NormalizedCriterion(key, CriterionResult.PARTIAL, comment);
    }

    private static NormalizedCriterion no(String key, String comment) {
        return new NormalizedCriterion(key, CriterionResult.NO, comment);
    }
}
