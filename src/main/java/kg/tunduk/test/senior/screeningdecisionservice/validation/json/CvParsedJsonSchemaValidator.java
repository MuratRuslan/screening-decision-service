package kg.tunduk.test.senior.screeningdecisionservice.validation.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Validates a raw {@code cv.parsed} payload against {@code contract/json-schema/cv-parsed.schema.json}
 * (draft 2020-12) before any business processing. Wraps com.networknt.json-schema-validator,
 * configured to report violations as JSON Pointers (e.g. {@code /criteria/0/key}) per TASK.md's
 * diagnostic requirement.
 */
public class CvParsedJsonSchemaValidator {

    private final JsonSchema schema;

    public CvParsedJsonSchemaValidator(JsonSchema schema) {
        this.schema = schema;
    }

    public static JsonSchema loadSchema(InputStream in) {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .pathType(PathType.JSON_POINTER)
                .build();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        return factory.getSchema(in, config);
    }

    public List<JsonPointerError> validate(JsonNode node) {
        Set<ValidationMessage> messages = schema.validate(node);
        return messages.stream()
                .map(m -> new JsonPointerError(m.getInstanceLocation().toString(), m.getMessage()))
                .toList();
    }
}
