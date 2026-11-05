package com.flavors.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and version shown at the top of the Swagger UI (/swagger-ui.html). */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI flavorsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Flavors API")
                .description("REST API of the Flavors platform. Most endpoints need a login session and, "
                        + "for state-changing requests, the X-XSRF-TOKEN header (see docs/Architecture.md §5).")
                .version("v1"));
    }
}
