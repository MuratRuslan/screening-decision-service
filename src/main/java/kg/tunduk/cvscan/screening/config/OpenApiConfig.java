package kg.tunduk.cvscan.screening.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI screeningDecisionServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Screening Decision Service API")
                .description("REST API сервиса принятия решений по кандидатам платформы CV-Scan")
                .version("1.0.0"));
    }
}
