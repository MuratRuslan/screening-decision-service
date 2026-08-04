package kg.tunduk.test.senior.screeningdecisionservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    // Batch-claiming query (SELECT ... FOR UPDATE SKIP LOCKED) added in Phase 7 alongside OutboxPublisher.
}
