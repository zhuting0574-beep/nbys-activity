package com.nbys.activity.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI nbysOpenApi(@Value("${spring.application.name:nbys-service}") String appName) {
        return new OpenAPI().info(new Info()
                .title(appName + " API")
                .version("0.1.0")
                .description("NBYS activity platform service APIs"));
    }
}
