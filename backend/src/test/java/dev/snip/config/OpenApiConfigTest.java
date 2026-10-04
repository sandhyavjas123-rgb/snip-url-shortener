package dev.snip.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {
    @Test
    void publishesTheConfiguredPublicEdgeWithoutATrailingSlash() {
        OpenAPI openApi = new OpenApiConfig().snipOpenApi("http://localhost:3000/");

        assertThat(openApi.getServers()).singleElement().satisfies(server -> {
            assertThat(server.getUrl()).isEqualTo("http://localhost:3000");
            assertThat(server.getDescription()).isEqualTo("Public application edge");
        });
    }
}
