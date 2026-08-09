package kg.tunduk.test.senior.screeningdecisionservice.config;

import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-detects any {@code com.fasterxml.jackson.databind.Module} bean and
 * registers it on the primary ObjectMapper. Without this, the OpenAPI Generator-produced
 * model classes' {@code JsonNullable<T>} fields (e.g. ErrorResponse.details,
 * DecisionResponse.overrideReason) would serialize as raw wrapper objects instead of their
 * unwrapped value.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonNullableModule jsonNullableModule() {
        return new JsonNullableModule();
    }
}
