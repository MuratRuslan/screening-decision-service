package kg.tunduk.test.senior.screeningdecisionservice.repository;

import kg.tunduk.test.senior.screeningdecisionservice.model.RuleSetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RuleSetRepository extends JpaRepository<RuleSetEntity, UUID> {

    Optional<RuleSetEntity> findByPositionAndVersion(String position, String version);

    /** The active rule-set for a position is the one with the greatest {@code activeFrom <= now}. */
    Optional<RuleSetEntity> findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(String position, Instant now);
}
