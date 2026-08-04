package kg.tunduk.test.senior.screeningdecisionservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks and returns up to {@code batchSize} NEW rows, skipping any already locked by a
     * concurrent poller (another instance, or another thread) instead of blocking on them -
     * this is what makes it safe to run the publisher on multiple app instances at once.
     * Native, because {@code FOR UPDATE SKIP LOCKED} has no JPA Criteria/JPQL equivalent.
     */
    @Query(value = "SELECT * FROM outbox_events WHERE status = 'NEW' ORDER BY created_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> claimBatch(@Param("batchSize") int batchSize);
}
