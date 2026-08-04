package kg.tunduk.test.senior.screeningdecisionservice.repository;

import jakarta.persistence.criteria.Predicate;
import kg.tunduk.test.senior.screeningdecisionservice.model.ScreeningDecisionEntity;
import kg.tunduk.test.senior.screeningdecisionservice.model.SourceVerdict;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.Decision;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a single {@link Specification} combining every non-null filter, so
 * {@code GET /decisions} compiles to one SQL query with the filtering done by
 * Postgres (WHERE/ORDER BY/LIMIT/OFFSET), never in application memory.
 */
public final class ScreeningDecisionSpecifications {

    private ScreeningDecisionSpecifications() {
    }

    public static Specification<ScreeningDecisionEntity> filter(String position, Decision decision,
                                                                  SourceVerdict sourceVerdict, Integer minScore,
                                                                  String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (position != null && !position.isBlank()) {
                predicates.add(cb.equal(root.get("position"), position));
            }
            if (decision != null) {
                predicates.add(cb.equal(root.get("decision"), decision));
            }
            if (sourceVerdict != null) {
                predicates.add(cb.equal(root.get("sourceVerdict"), sourceVerdict));
            }
            if (minScore != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("score"), minScore));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("candidateId")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
