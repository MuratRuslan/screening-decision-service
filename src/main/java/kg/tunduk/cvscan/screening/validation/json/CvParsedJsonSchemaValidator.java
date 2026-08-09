package kg.tunduk.cvscan.screening.validation.json;

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
 * Проверяет исходный payload {@code cv.parsed} по схеме {@code contract/json-schema/cv-parsed.schema.json}
 * (draft 2020-12) до начала бизнес-обработки. Обёртка над com.networknt.json-schema-validator,
 * настроена сообщать об ошибках в виде JSON Pointer (например, {@code /criteria/0/key}) согласно
 * требованиям диагностики из TASK.md.
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
