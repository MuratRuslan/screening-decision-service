package kg.tunduk.test.senior.screeningdecisionservice.integration;

import kg.tunduk.test.senior.screeningdecisionservice.TestcontainersConfiguration;
import kg.tunduk.test.senior.screeningdecisionservice.dto.rest.DecisionPage;
import kg.tunduk.test.senior.screeningdecisionservice.scoring.Decision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises GET /decisions filter/sort/pagination combinations against the V5-V7 seed data.
 * Assertions are property-based (score thresholds, sort order, filter membership) rather
 * than exact row counts, since other IT classes in the same suite (CvParsedFlowIT, DlqIT,
 * OverrideIT) may share the cached Spring context / Testcontainers instances and add or
 * mutate rows - this test must not depend on execution order relative to those. Requires
 * Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DecisionQueryIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void filtersByPositionAndDecisionAndSortsByScoreDescending() {
        String url = UriComponentsBuilder.fromPath("/api/v1/decisions")
                .queryParam("position", "java-senior")
                .queryParam("decision", "AUTO_APPROVE")
                .queryParam("sort", "score,desc")
                .queryParam("size", "50")
                .toUriString();

        ResponseEntity<DecisionPage> response = restTemplate.getForEntity(url, DecisionPage.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        DecisionPage page = response.getBody();
        assertThat(page).isNotNull();
        assertThat(page.content()).isNotEmpty();
        assertThat(page.content()).allSatisfy(d -> {
            assertThat(d.position()).isEqualTo("java-senior");
            assertThat(d.decision()).isEqualTo(Decision.AUTO_APPROVE);
        });
        for (int i = 1; i < page.content().size(); i++) {
            assertThat(page.content().get(i - 1).score()).isGreaterThanOrEqualTo(page.content().get(i).score());
        }
    }

    @Test
    void filtersByMinScore() {
        String url = UriComponentsBuilder.fromPath("/api/v1/decisions")
                .queryParam("position", "java-senior")
                .queryParam("minScore", "90")
                .queryParam("size", "50")
                .toUriString();

        DecisionPage page = restTemplate.getForEntity(url, DecisionPage.class).getBody();

        assertThat(page).isNotNull();
        assertThat(page.content()).isNotEmpty();
        assertThat(page.content()).allSatisfy(d -> assertThat(d.score()).isGreaterThanOrEqualTo(90));
    }

    @Test
    void searchMatchesCandidateIdSubstring() {
        String url = UriComponentsBuilder.fromPath("/api/v1/decisions")
                .queryParam("search", "asanov-bakyt")
                .toUriString();

        DecisionPage page = restTemplate.getForEntity(url, DecisionPage.class).getBody();

        assertThat(page).isNotNull();
        assertThat(page.content()).isNotEmpty();
        assertThat(page.content()).allSatisfy(d -> assertThat(d.candidateId()).contains("asanov-bakyt"));
    }

    @Test
    void paginationHonorsRequestedPageSize() {
        String url = UriComponentsBuilder.fromPath("/api/v1/decisions")
                .queryParam("position", "java-senior")
                .queryParam("size", "2")
                .queryParam("page", "0")
                .toUriString();

        DecisionPage page = restTemplate.getForEntity(url, DecisionPage.class).getBody();

        assertThat(page).isNotNull();
        assertThat(page.content()).hasSizeLessThanOrEqualTo(2);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(13); // at least the V6 seed rows
    }

    @Test
    void rejectsUnsupportedSortField() {
        String url = UriComponentsBuilder.fromPath("/api/v1/decisions")
                .queryParam("sort", "email,asc")
                .toUriString();

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
