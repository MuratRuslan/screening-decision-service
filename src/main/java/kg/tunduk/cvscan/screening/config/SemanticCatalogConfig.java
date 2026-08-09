package kg.tunduk.cvscan.screening.config;

import kg.tunduk.cvscan.screening.semantic.CriteriaCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class SemanticCatalogConfig {

    @Bean
    public CriteriaCatalog criteriaCatalog() throws IOException {
        try (final InputStream in = new ClassPathResource("semantic/criteria-catalog.json").getInputStream()) {
            return CriteriaCatalog.parse(in);
        }
    }
}
