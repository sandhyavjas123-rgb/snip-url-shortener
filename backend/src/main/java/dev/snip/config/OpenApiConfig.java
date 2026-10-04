package dev.snip.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI snipOpenApi(@Value("${app.public-base-url}") String publicBaseUrl) {
        return new OpenAPI().info(new Info().title("Snip URL Shortener API").version("1.0.0")
                .description("Create, manage, resolve, and inspect short links."))
                .servers(List.of(new Server().url(publicBaseUrl.replaceAll("/+$", ""))
                        .description("Public application edge")));
    }
}
