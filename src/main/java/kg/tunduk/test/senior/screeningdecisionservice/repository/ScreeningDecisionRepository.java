package kg.tunduk.test.senior.screeningdecisionservice.repository;

import kg.tunduk.test.senior.screeningdecisionservice.model.ScreeningDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ScreeningDecisionRepository
        extends JpaRepository<ScreeningDecisionEntity, UUID>, JpaSpecificationExecutor<ScreeningDecisionEntity> {

    Optional<ScreeningDecisionEntity> findByCandidateIdAndParsedAt(String candidateId, Instant parsedAt);

    /**
     * Multiple decisions per candidate are legitimate (re-screenings at different
     * {@code parsedAt}); "latest" is simply the most recently decided one.
     */
    Optional<ScreeningDecisionEntity> findFirstByCandidateIdOrderByDecidedAtDesc(String candidateId);
}
