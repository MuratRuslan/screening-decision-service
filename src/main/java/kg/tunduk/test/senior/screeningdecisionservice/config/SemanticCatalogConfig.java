package kg.tunduk.test.senior.screeningdecisionservice.config;

import kg.tunduk.test.senior.screeningdecisionservice.semantic.CriteriaCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class SemanticCatalogConfig {

    @Bean
    public CriteriaCatalog criteriaCatalog() throws IOException {
        try (InputStream in = new ClassPathResource("semantic/criteria-catalog.json").getInputStream()) {
            return CriteriaCatalog.parse(in);
        }
    }
}
