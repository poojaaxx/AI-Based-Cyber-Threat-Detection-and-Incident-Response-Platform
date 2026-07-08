package com.cyberguard.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${ai-service.base-url}")
    private String aiServiceBaseUrl;

    @Bean
    public WebClient aiServiceWebClient() {
        // Render's render.yaml wires this from another service's "hostport" (host:port,
        // no scheme) for private-network calls, whereas local/.env values are already
        // full URLs (http://localhost:8000). Normalize so both resolve correctly.
        String url = aiServiceBaseUrl.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
                ? aiServiceBaseUrl
                : "http://" + aiServiceBaseUrl;
        return WebClient.builder()
                .baseUrl(url)
                .build();
    }
}
