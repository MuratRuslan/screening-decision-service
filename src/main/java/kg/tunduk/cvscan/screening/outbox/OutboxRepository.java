package kg.tunduk.cvscan.screening.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Блокирует и возвращает до {@code batchSize} строк NEW, пропуская те, что уже
     * заблокированы другим параллельным поллером (другим инстансом или потоком), вместо ожидания -
     * именно это делает безопасным запуск publisher'а сразу на нескольких инстансах приложения.
     * Нативный запрос, потому что у {@code FOR UPDATE SKIP LOCKED} нет аналога в JPA Criteria/JPQL.
     */
    @Query(value = "SELECT * FROM outbox_events WHERE status = 'NEW' ORDER BY created_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> claimBatch(@Param("batchSize") int batchSize);
}
