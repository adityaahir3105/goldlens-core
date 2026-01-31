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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class NewsApiClient {

    private static final Logger log = LoggerFactory.getLogger(NewsApiClient.class);

    private static final String PROVIDER_NAME = "newsapi";
    private static final int MAX_ARTICLES = 10;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public NewsApiClient(
            @Value("${news.primary.base-url:https://newsapi.org/v2}") String baseUrl,
            @Value("${news.primary.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Optional<List<GoldNewsItem>> fetchGoldNews() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[NewsAPI] API key not configured");
            return Optional.empty();
        }

        try {
            String fromDate = Instant.now().minus(72, ChronoUnit.HOURS).toString().substring(0, 10);
            
            // Strict gold-focused query - only gold and macro factors affecting gold
            String query = "gold OR \"gold price\" OR \"precious metals\" OR \"Federal Reserve\" OR \"interest rate\" OR \"real yields\" OR DXY OR \"central bank\"";

            String responseBody = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/everything")
                            .queryParam("q", query)
                            .queryParam("from", fromDate)
                            .queryParam("sortBy", "relevancy")
                            .queryParam("language", "en")
                            .queryParam("pageSize", MAX_ARTICLES)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("[NewsAPI] Empty response");
                return Optional.empty();
            }

            log.debug("[NewsAPI] Raw response: {}", responseBody);

            JsonNode root = objectMapper.readTree(responseBody);
            
            if (!"ok".equals(root.path("status").asText())) {
                log.error("[NewsAPI] API error: {}", root.path("message").asText());
                return Optional.empty();
            }

            JsonNode articles = root.path("articles");
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

            log.info("[NewsAPI] Fetched {} articles", items.size());
            return Optional.of(items);

        } catch (Exception e) {
            log.error("[NewsAPI] Failed to fetch news: {}", e.getMessage());
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
