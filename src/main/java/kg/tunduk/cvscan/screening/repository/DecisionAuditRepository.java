package kg.tunduk.cvscan.screening.repository;

import kg.tunduk.cvscan.screening.model.DecisionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DecisionAuditRepository extends JpaRepository<DecisionAuditEntity, UUID> {

    List<DecisionAuditEntity> findByDecisionIdOrderByCreatedAtAsc(UUID decisionId);
}
