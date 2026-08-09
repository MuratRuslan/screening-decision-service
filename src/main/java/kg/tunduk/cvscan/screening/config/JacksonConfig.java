package kg.tunduk.cvscan.screening.config;

import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot автоматически находит любой бин {@code com.fasterxml.jackson.databind.Module}
 * и регистрирует его в основном ObjectMapper. Без этого поля {@code JsonNullable<T>} в
 * моделях, сгенерированных OpenAPI Generator (например, ErrorResponse.details,
 * DecisionResponse.overrideReason), сериализовались бы как сырые объекты-обёртки, а не как
 * их развёрнутое значение.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonNullableModule jsonNullableModule() {
        return new JsonNullableModule();
    }
}
