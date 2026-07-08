package com.cyberguard.platform.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a JWT Bearer security scheme with springdoc so Swagger UI renders
 * an "Authorize" button. The scheme is applied globally via the top-level
 * @SecurityRequirement; individual public endpoints (e.g. AuthController)
 * opt out with @SecurityRequirements() to avoid implying they need a token.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "CyberGuard Platform API",
                version = "1.0.0",
                description = "AI-Based Cyber Threat Detection and Incident Response Platform - Backend REST API"
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste the accessToken returned by /api/v1/auth/login (without the 'Bearer ' prefix)"
)
public class OpenApiConfig {
}
