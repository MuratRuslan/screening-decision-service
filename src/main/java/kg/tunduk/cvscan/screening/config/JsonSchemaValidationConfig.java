package kg.tunduk.cvscan.screening.config;

import kg.tunduk.cvscan.screening.validation.json.CvParsedJsonSchemaValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class JsonSchemaValidationConfig {

    @Bean
    public CvParsedJsonSchemaValidator cvParsedJsonSchemaValidator() throws IOException {
        try (InputStream in = new ClassPathResource("contract/json-schema/cv-parsed.schema.json").getInputStream()) {
            return new CvParsedJsonSchemaValidator(CvParsedJsonSchemaValidator.loadSchema(in));
        }
    }
}
