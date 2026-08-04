package kg.tunduk.test.senior.screeningdecisionservice.validation.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvParsedJsonSchemaValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static CvParsedJsonSchemaValidator validator;

    @BeforeAll
    static void loadValidator() throws IOException {
        try (InputStream in = CvParsedJsonSchemaValidatorTest.class.getClassLoader()
                .getResourceAsStream("contract/json-schema/cv-parsed.schema.json")) {
            JsonSchema schema = CvParsedJsonSchemaValidator.loadSchema(in);
            validator = new CvParsedJsonSchemaValidator(schema);
        }
    }

    @Test
    void validSampleEventProducesNoErrors() throws IOException {
        JsonNode node = MAPPER.readTree(Path.of("java-senior/test-events/cv-parsed-sample.json").toFile());

        List<JsonPointerError> errors = validator.validate(node);

        assertThat(errors).isEmpty();
    }

    @Test
    void missingRequiredFieldIsReportedAtRootPointer() throws IOException {
        JsonNode node = MAPPER.readTree("""
                {
                  "eventId": "660e8400-e29b-41d4-a716-446655440000",
                  "candidateId": "senior-test",
                  "parsedAt": "2026-05-20T09:00:00Z",
                  "name": "Тест Тестов",
                  "position": "java-senior",
                  "verdict": "FIT",
                  "criteria": []
                }
                """);
        // "email" is required but missing

        List<JsonPointerError> errors = validator.validate(node);

        assertThat(errors).isNotEmpty();
        assertThat(errors).anySatisfy(e -> assertThat(e.message()).containsIgnoringCase("email"));
    }

    @Test
    void invalidCriteriaKeyPatternIsReportedWithJsonPointer() throws IOException {
        String json = Files.readString(Path.of("java-senior/test-events/cv-parsed-sample.json"));
        JsonNode node = MAPPER.readTree(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.get("criteria").get(0)).put("key", "Java Spring!");

        List<JsonPointerError> errors = validator.validate(node);

        assertThat(errors).anySatisfy(e -> assertThat(e.pointer()).isEqualTo("/criteria/0/key"));
    }

    @Test
    void invalidVerdictEnumValueIsReportedWithJsonPointer() throws IOException {
        String json = Files.readString(Path.of("java-senior/test-events/cv-parsed-sample.json"));
        JsonNode node = MAPPER.readTree(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("verdict", "MAYBE");

        List<JsonPointerError> errors = validator.validate(node);

        assertThat(errors).anySatisfy(e -> assertThat(e.pointer()).isEqualTo("/verdict"));
    }

    @Test
    void unknownAdditionalPropertyIsRejected() throws IOException {
        String json = Files.readString(Path.of("java-senior/test-events/cv-parsed-sample.json"));
        JsonNode node = MAPPER.readTree(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("unexpectedField", "boom");

        List<JsonPointerError> errors = validator.validate(node);

        assertThat(errors).isNotEmpty();
    }
}
