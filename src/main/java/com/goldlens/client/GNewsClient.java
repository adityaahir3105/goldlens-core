package com.goldlens.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldlens.dto.GoldNewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class GNewsClient {

    private static final Logger log = LoggerFactory.getLogger(GNewsClient.class);

    private static final String PROVIDER_NAME = "gnews";
    private static final int MAX_ARTICLES = 10;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GNewsClient(
            @Value("${news.fallback.base-url:https://gnews.io/api/v4}") String baseUrl,
            @Value("${news.fallback.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Optional<List<GoldNewsItem>> fetchGoldNews() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[GNews] API key not configured");
            return Optional.empty();
        }

        try {
            // Strict gold-focused query
            String query = "gold price OR precious metals OR Federal Reserve OR central bank gold";

            String responseBody = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("lang", "en")
                            .queryParam("sortby", "relevance")
                            .queryParam("max", MAX_ARTICLES)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("[GNews] Empty response");
                return Optional.empty();
            }

            log.debug("[GNews] Raw response: {}", responseBody);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode articles = root.path("articles");
            
            if (articles.isMissingNode() || !articles.isArray()) {
                log.error("[GNews] Invalid response structure");
                return Optional.empty();
            }

            List<GoldNewsItem> items = new ArrayList<>();

            for (JsonNode article : articles) {
                GoldNewsItem item = GoldNewsItem.builder()
                        .title(article.path("title").asText())
                        .source(article.path("source").path("name").asText())
                        .url(article.path("url").asText())
                        .publishedAt(article.path("publishedAt").asText())
                        .build();
                items.add(item);
            }

            log.info("[GNews] Fetched {} articles", items.size());
            return Optional.of(items);

        } catch (Exception e) {
            log.error("[GNews] Failed to fetch news: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public String getProviderName() {
        return PROVIDER_NAME;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
