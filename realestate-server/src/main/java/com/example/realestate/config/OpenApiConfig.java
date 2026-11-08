package com.example.realestate.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI realestateOpenApi() {
        return new OpenAPI().info(new Info()
                .title("房产中介管理系统 API")
                .description("W1 认证与工程基座；Base URL /api/v1")
                .version("v1"));
    }
}
