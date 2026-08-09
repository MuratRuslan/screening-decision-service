package kg.tunduk.cvscan.screening.repository;

import jakarta.persistence.criteria.Predicate;
import kg.tunduk.cvscan.screening.model.ScreeningDecisionEntity;
import kg.tunduk.cvscan.screening.model.SourceVerdict;
import kg.tunduk.cvscan.screening.scoring.Decision;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Собирает единый {@link Specification} из всех непустых фильтров, чтобы
 * {@code GET /decisions} превращался в один SQL-запрос, а фильтрация выполнялась
 * Postgres (WHERE/ORDER BY/LIMIT/OFFSET), а не в памяти приложения.
 */
public final class ScreeningDecisionSpecifications {

    private ScreeningDecisionSpecifications() {
    }

    public static Specification<ScreeningDecisionEntity> filter(final String position, final Decision decision,
                                                                  final SourceVerdict sourceVerdict, final Integer minScore,
                                                                  final String search) {
        return (root, query, cb) -> {
            final List<Predicate> predicates = new ArrayList<>();

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
                final String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
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
