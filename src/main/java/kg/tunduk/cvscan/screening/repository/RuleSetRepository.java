package kg.tunduk.cvscan.screening.repository;

import kg.tunduk.cvscan.screening.model.RuleSetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RuleSetRepository extends JpaRepository<RuleSetEntity, UUID> {

    Optional<RuleSetEntity> findByPositionAndVersion(String position, String version);

    /** Активный rule-set для позиции - тот, у которого {@code activeFrom <= now} максимальный. */
    Optional<RuleSetEntity> findFirstByPositionAndActiveFromLessThanEqualOrderByActiveFromDesc(String position, Instant now);
}
