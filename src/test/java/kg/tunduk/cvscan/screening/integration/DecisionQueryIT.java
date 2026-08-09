package kg.tunduk.cvscan.screening.integration;

import kg.tunduk.cvscan.screening.TestcontainersConfiguration;
import kg.tunduk.cvscan.screening.generated.rest.model.Decision;
import kg.tunduk.cvscan.screening.generated.rest.model.DecisionPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет комбинации фильтров/сортировки/пагинации GET /decisions на seed-данных V5-V7.
 * Проверки основаны на свойствах (пороги score, порядок сортировки, принадлежность фильтру),
 * а не на точном числе строк, так как другие IT-классы в этом же наборе (CvParsedFlowIT, DlqIT,
 * OverrideIT) могут использовать общий кэшированный Spring-контекст / Testcontainers и
 * добавлять или менять строки - этот тест не должен зависеть от порядка выполнения относительно них.
 * Требует Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DecisionQueryIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void filtersByPositionAndDecisionAndSortsByScoreDescending() {
        final String url = UriComponentsBuilder.fromPath("/api/v1/decisions")
                .queryParam("position", "java-senior")
                .queryParam("decision", "AUTO_APPROVE")
                .queryParam("sort", "score,desc")
                .queryParam("size", "50")
                .toUriString();

        final ResponseEntity<DecisionPage> response = restTemplate.getForEntity(url, DecisionPage.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        final DecisionPage page = response.getBody();
        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).allSatisfy(d -> {
            assertThat(d.getPosition()).isEqualTo("java-senior");
            assertThat(d.getDecision()).isEqualTo(Decision.AUTO_APPROVE);
        });
        for (int i = 1; i < page.getContent().size(); i++) {
            assertThat(page.getContent().get(i - 1).getScore()).isGreaterThanOrEqualTo(page.getContent().get(i).getScore());
        }
    }

    @Test
    void filtersByMinScore() {
        final String url = UriComponentsBuilder.fromPath("/api/v1/decisions")
                .queryParam("position", "java-senior")
                .queryParam("minScore", "90")
                .queryParam("size", "50")
                .toUriString();

        final DecisionPage page = restTemplate.getForEntity(url, DecisionPage.class).getBody();

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).allSatisfy(d -> assertThat(d.getScore()).isGreaterThanOrEqualTo(90));
    }

    @Test
    void searchMatchesCandidateIdSubstring() {
        final String url = UriComponentsBuilder.fromPath("/api/v1/decisions")
                .queryParam("search", "asanov-bakyt")
                .toUriString();

        final DecisionPage page = restTemplate.getForEntity(url, DecisionPage.class).getBody();

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).allSatisfy(d -> assertThat(d.getCandidateId()).contains("asanov-bakyt"));
    }

    @Test
    void paginationHonorsRequestedPageSize() {
        final String url = UriComponentsBuilder.fromPath("/api/v1/decisions")
                .queryParam("position", "java-senior")
                .queryParam("size", "2")
                .queryParam("page", "0")
                .toUriString();

        final DecisionPage page = restTemplate.getForEntity(url, DecisionPage.class).getBody();

        assertThat(page).isNotNull();
        assertThat(page.getContent()).hasSizeLessThanOrEqualTo(2);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(13); // как минимум строки seed из V6
    }

    @Test
    void rejectsUnsupportedSortField() {
        final String url = UriComponentsBuilder.fromPath("/api/v1/decisions")
                .queryParam("sort", "email,asc")
                .toUriString();

        final ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
