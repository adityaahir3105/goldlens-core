package com.goldlens.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class FredConfig {

    @Value("${fred.api.key}")
    private String apiKey;

    @Bean
    public WebClient fredWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.stlouisfed.org/fred")
                .build();
    }

    @Bean
    public String fredApiKey() {
        return apiKey;
    }
}
