package kg.tunduk.test.senior.screeningdecisionservice.integration;

import kg.tunduk.test.senior.screeningdecisionservice.TestcontainersConfiguration;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.AuditEntryResponse;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.DecisionOverrideRequest;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.DecisionResponse;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.ErrorResponse;
import kg.tunduk.test.senior.screeningdecisionservice.model.AuditAction;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.Decision;
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
 * Two seeded, never-overridden decisions (V6 migration, version 1) are used - one per test
 * method - so the two scenarios don't depend on execution order. Requires Docker.
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
        HttpHeaders headers = new HttpHeaders();
        headers.set("expectedVersion", "1");
        DecisionOverrideRequest body = new DecisionOverrideRequest(Decision.AUTO_REJECT,
                "Интеграционный тест: подтверждено ручное отклонение после интервью");

        ResponseEntity<DecisionResponse> response = restTemplate.exchange(
                "/api/v1/decisions/" + HAPPY_PATH_DECISION_ID + "/override",
                HttpMethod.PATCH, new HttpEntity<>(body, headers), DecisionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().decision()).isEqualTo(Decision.AUTO_REJECT);
        assertThat(response.getBody().overridden()).isTrue();
        assertThat(response.getBody().version()).isEqualTo(2);

        ResponseEntity<AuditEntryResponse[]> audit = restTemplate.getForEntity(
                "/api/v1/decisions/" + HAPPY_PATH_DECISION_ID + "/audit", AuditEntryResponse[].class);
        assertThat(audit.getBody()).isNotNull();
        assertThat(audit.getBody()).anySatisfy(a -> assertThat(a.action()).isEqualTo(AuditAction.OVERRIDDEN));
    }

    @Test
    void staleExpectedVersionReturnsVersionConflict() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("expectedVersion", "999");
        DecisionOverrideRequest body = new DecisionOverrideRequest(Decision.AUTO_APPROVE,
                "Интеграционный тест: заведомо устаревшая версия");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/decisions/" + STALE_VERSION_DECISION_ID + "/override",
                HttpMethod.PATCH, new HttpEntity<>(body, headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("VERSION_CONFLICT");
    }
}
