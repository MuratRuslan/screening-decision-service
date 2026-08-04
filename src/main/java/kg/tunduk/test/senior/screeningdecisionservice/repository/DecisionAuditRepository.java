package kg.tunduk.test.senior.screeningdecisionservice.repository;

import kg.tunduk.test.senior.screeningdecisionservice.model.DecisionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DecisionAuditRepository extends JpaRepository<DecisionAuditEntity, UUID> {

    List<DecisionAuditEntity> findByDecisionIdOrderByCreatedAtAsc(UUID decisionId);
}
