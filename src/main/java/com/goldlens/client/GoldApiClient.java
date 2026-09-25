package com.goldlens.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * GoldAPI client for fetching real-time and historical gold prices.
 */
@Component
public class GoldApiClient {

    private static final Logger log = LoggerFactory.getLogger(GoldApiClient.class);

    private static final String SOURCE = "GoldAPI_BACKFILL";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GoldApiClient(
            @Value("${goldapi.base-url:https://www.goldapi.io/api}") String baseUrl,
            @Value("${goldapi.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        
        log.warn("[GoldApiClient] Initialized for BACKFILL ONLY. This should not be used in production runtime.");
    }

    /**
     * Fetches gold price for a specific date from GoldAPI.
     * Used only for historical backfill.
     */
    public Optional<HistoricalPrice> fetchPriceForDate(LocalDate date) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[backfill] GoldAPI key not configured");
            return Optional.empty();
        }

        String dateStr = date.format(DATE_FORMAT);
        
        try {
            String responseBody = webClient.get()
                    .uri("/XAU/USD/" + dateStr)
                    .header("x-access-token", apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("[backfill] Empty response for date {}", date);
                return Optional.empty();
            }

            log.debug("[backfill] Raw response for {}: {}", date, responseBody);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

            Object priceObj = response.get("price");
            if (priceObj == null) {
                log.warn("[backfill] No price in response for date {}", date);
                return Optional.empty();
            }

            BigDecimal price = new BigDecimal(priceObj.toString());
            log.info("[backfill] Fetched price {} for date {} from GoldAPI", price, date);

            return Optional.of(new HistoricalPrice(date, price, SOURCE));

        } catch (WebClientResponseException e) {
            log.error("[backfill] GoldAPI request failed for {}: {} {}", date, e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.error("[backfill] Failed to parse GoldAPI response for {}: {}", date, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("[backfill] Unexpected error fetching price for {}: {}", date, e.getMessage());
            return Optional.empty();
        }
    }

    public com.goldlens.dto.GoldPriceSnapshot fetchLatestGoldPrice() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new com.goldlens.exception.GoldApiUnavailableException("GoldAPI key not configured", 503, "CONFIG_ERROR", "N/A");
        }
        
        try {
            String responseBody = webClient.get()
                    .uri("/XAU/USD")
                    .header("x-access-token", apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                throw new com.goldlens.exception.GoldApiUnavailableException("GoldAPI returned null response", 502, "NULL_RESPONSE", "N/A");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            Object priceObj = response.get("price");
            if (priceObj == null) {
                throw new com.goldlens.exception.GoldApiUnavailableException("GoldAPI response missing price field", 502, "INVALID_RESPONSE", "N/A");
            }

            BigDecimal price = new BigDecimal(priceObj.toString());
            return com.goldlens.dto.GoldPriceSnapshot.builder()
                    .price(price)
                    .currency("USD")
                    .unit("oz")
                    .asOf(java.time.LocalDateTime.now())
                    .source(SOURCE)
                    .build();

        } catch (WebClientResponseException e) {
            throw new com.goldlens.exception.GoldApiUnavailableException("GoldAPI request failed: " + e.getMessage(), e.getStatusCode().value(), "API_ERROR", "N/A", e);
        } catch (Exception e) {
            throw new com.goldlens.exception.GoldApiUnavailableException("Failed to fetch gold price from GoldAPI: " + e.getMessage(), 502, "UNEXPECTED_ERROR", "N/A", e);
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public record HistoricalPrice(LocalDate date, BigDecimal price, String source) {}
}
