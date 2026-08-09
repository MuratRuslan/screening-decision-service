package kg.tunduk.cvscan.screening.repository;

import kg.tunduk.cvscan.screening.model.ScreeningDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ScreeningDecisionRepository
        extends JpaRepository<ScreeningDecisionEntity, UUID>, JpaSpecificationExecutor<ScreeningDecisionEntity> {

    Optional<ScreeningDecisionEntity> findByCandidateIdAndParsedAt(String candidateId, Instant parsedAt);

    /**
     * У кандидата может быть несколько решений (повторный скрининг с другим
     * {@code parsedAt}); "последнее" - это просто решение с самой поздней датой.
     */
    Optional<ScreeningDecisionEntity> findFirstByCandidateIdOrderByDecidedAtDesc(String candidateId);
}
