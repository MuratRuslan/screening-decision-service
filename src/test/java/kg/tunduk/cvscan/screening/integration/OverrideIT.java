package kg.tunduk.cvscan.screening.integration;

import kg.tunduk.cvscan.screening.TestcontainersConfiguration;
import kg.tunduk.cvscan.screening.generated.rest.model.AuditAction;
import kg.tunduk.cvscan.screening.generated.rest.model.AuditEntry;
import kg.tunduk.cvscan.screening.generated.rest.model.Decision;
import kg.tunduk.cvscan.screening.generated.rest.model.DecisionOverrideRequest;
import kg.tunduk.cvscan.screening.generated.rest.model.DecisionResponse;
import kg.tunduk.cvscan.screening.generated.rest.model.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Используются два seed-решения без переопределений (миграция V6, версия 1) - по одному
 * на тестовый метод - чтобы оба сценария не зависели от порядка выполнения. Требует Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OverrideIT {

    private static final String HAPPY_PATH_DECISION_ID = "22222222-2222-2222-2222-222222222203";
    private static final String STALE_VERSION_DECISION_ID = "22222222-2222-2222-2222-222222222204";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void correctExpectedVersionAppliesOverrideAndRecordsAudit() {
        final HttpHeaders headers = new HttpHeaders();
        headers.set("expectedVersion", "1");
        final DecisionOverrideRequest body = new DecisionOverrideRequest(Decision.AUTO_REJECT,
                "Интеграционный тест: подтверждено ручное отклонение после интервью");

        final ResponseEntity<DecisionResponse> response = restTemplate.exchange(
                "/api/v1/decisions/" + HAPPY_PATH_DECISION_ID + "/override",
                HttpMethod.PATCH, new HttpEntity<>(body, headers), DecisionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDecision()).isEqualTo(Decision.AUTO_REJECT);
        assertThat(response.getBody().getOverridden()).isTrue();
        assertThat(response.getBody().getVersion()).isEqualTo(2);

        final ResponseEntity<AuditEntry[]> audit = restTemplate.getForEntity(
                "/api/v1/decisions/" + HAPPY_PATH_DECISION_ID + "/audit", AuditEntry[].class);
        assertThat(audit.getBody()).isNotNull();
        assertThat(audit.getBody()).anySatisfy(a -> assertThat(a.getAction()).isEqualTo(AuditAction.OVERRIDDEN));
    }

    @Test
    void staleExpectedVersionReturnsVersionConflict() {
        final HttpHeaders headers = new HttpHeaders();
        headers.set("expectedVersion", "999");
        final DecisionOverrideRequest body = new DecisionOverrideRequest(Decision.AUTO_APPROVE,
                "Интеграционный тест: заведомо устаревшая версия");

        final ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/decisions/" + STALE_VERSION_DECISION_ID + "/override",
                HttpMethod.PATCH, new HttpEntity<>(body, headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("VERSION_CONFLICT");
    }
}
