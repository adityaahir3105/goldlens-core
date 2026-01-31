package com.goldlens.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldlens.dto.GoldPriceSnapshot;
import com.goldlens.dto.GoldPricezResponse;
import com.goldlens.exception.GoldApiUnavailableException;
import com.goldlens.exception.GoldPricezParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * Client for GoldPricez.com API.
 * Fetches real-time gold prices in USD per ounce.
 * 
 * API docs: https://goldpricez.com/about/api
 * Rate limit: 30-60 requests/hour
 * Auth: X-API-KEY header
 */
@Component
public class GoldPricezClient {

    private static final Logger log = LoggerFactory.getLogger(GoldPricezClient.class);

    private static final String SOURCE = "GoldPricez";
    private static final String CURRENCY = "USD";
    private static final String UNIT = "oz";

    // GoldPricez timestamp format: "19-12-2018 01:16:01 pm"
    private static final DateTimeFormatter GOLDPRICEZ_DATE_FORMAT = 
            DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a", Locale.ENGLISH);

    private final WebClient webClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public GoldPricezClient(
            @Value("${goldpricez.base-url:https://goldpricez.com/api}") String baseUrl,
            @Value("${goldpricez.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-KEY", apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Fetches the latest gold price (XAU/USD per ounce) from GoldPricez.
     * Throws GoldApiUnavailableException on any failure.
     */
    @SuppressWarnings("unchecked")
    public GoldPriceSnapshot fetchLatestGoldPrice() {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);

        try {
            if (apiKey == null || apiKey.isBlank()) {
                log.error("[requestId={}] [errorType=CONFIG_ERROR] GoldPricez API key not configured", requestId);
                throw new GoldApiUnavailableException(
                        "GoldPricez API key not configured",
                        503,
                        "CONFIG_ERROR",
                        requestId
                );
            }

            // GoldPricez API returns JSON with incorrect Content-Type (text/html or similar),
            // causing Jackson's automatic deserialization to fail. We fetch as String and parse manually.
            String responseBody;
            try {
                responseBody = webClient.get()
                        .uri("/rates/currency/usd/measure/ounce")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            } catch (WebClientResponseException e) {
                String errorType = mapStatusToErrorType(e.getStatusCode().value());
                log.error("[requestId={}] [errorType={}] GoldPricez request failed: {} {}",
                        requestId, errorType, e.getStatusCode().value(), e.getMessage());
                throw new GoldApiUnavailableException(
                        "GoldPricez request failed: " + e.getMessage(),
                        e.getStatusCode().value(),
                        errorType,
                        requestId,
                        e
                );
            }

            if (responseBody == null || responseBody.isBlank()) {
                log.error("[requestId={}] [errorType=NULL_RESPONSE] GoldPricez returned null or empty response", requestId);
                throw new GoldApiUnavailableException(
                        "GoldPricez returned null or empty response",
                        502,
                        "NULL_RESPONSE",
                        requestId
                );
            }

            // Log raw response for debugging
            log.debug("[requestId={}] Raw GoldPricez response: {}", requestId, responseBody);

            // GoldPricez API may return JSON wrapped as a string (double-encoded).
            // Example: "{\"ounce_price_usd\":\"4895.440\"}"
            // We need to unwrap it first if it starts with a quote.
            String jsonContent = unwrapJsonString(responseBody, requestId);
            log.debug("[requestId={}] Unwrapped JSON content: {}", requestId, jsonContent);

            // Parse JSON string using Jackson ObjectMapper into DTO
            GoldPricezResponse response;
            try {
                response = objectMapper.readValue(jsonContent, GoldPricezResponse.class);
            } catch (JsonProcessingException e) {
                log.error("[requestId={}] [errorType=JSON_PARSE_ERROR] Invalid GoldPricez response: {}",
                        requestId, e.getMessage());
                throw new GoldPricezParseException(
                        "Invalid GoldPricez response: " + e.getMessage(),
                        requestId,
                        responseBody,
                        e
                );
            }

            // Validate required field
            if (response.getOuncePriceUsd() == null || response.getOuncePriceUsd().isBlank()) {
                log.error("[requestId={}] [errorType=INVALID_RESPONSE] GoldPricez response missing ounce_price_usd field", requestId);
                throw new GoldApiUnavailableException(
                        "GoldPricez response missing ounce_price_usd field",
                        502,
                        "INVALID_RESPONSE",
                        requestId
                );
            }

            // Convert price string to BigDecimal
            BigDecimal price;
            try {
                price = new BigDecimal(response.getOuncePriceUsd());
            } catch (NumberFormatException e) {
                log.error("[requestId={}] [errorType=INVALID_RESPONSE] Invalid price format: {}",
                        requestId, response.getOuncePriceUsd());
                throw new GoldApiUnavailableException(
                        "Invalid price format in GoldPricez response",
                        502,
                        "INVALID_RESPONSE",
                        requestId,
                        e
                );
            }

            // Parse timestamp from "gmt_ounce_price_usd_updated" field
            LocalDateTime asOf = LocalDateTime.now();
            if (response.getUpdatedAt() != null && !response.getUpdatedAt().isBlank()) {
                try {
                    asOf = LocalDateTime.parse(response.getUpdatedAt(), GOLDPRICEZ_DATE_FORMAT);
                } catch (DateTimeParseException e) {
                    log.debug("[requestId={}] Could not parse timestamp '{}', using current time", 
                            requestId, response.getUpdatedAt());
                }
            }

            GoldPriceSnapshot snapshot = GoldPriceSnapshot.builder()
                    .price(price)
                    .currency(CURRENCY)
                    .unit(UNIT)
                    .asOf(asOf)
                    .source(SOURCE)
                    .build();

            log.info("[requestId={}] Fetched gold price from GoldPricez: {} {}/{}", 
                    requestId, price, CURRENCY, UNIT);
            return snapshot;

        } catch (GoldApiUnavailableException e) {
            throw e;
        } catch (GoldPricezParseException e) {
            throw new GoldApiUnavailableException(
                    e.getMessage(),
                    502,
                    "JSON_PARSE_ERROR",
                    e.getRequestId(),
                    e
            );
        } catch (Exception e) {
            log.error("[requestId={}] [errorType=UNEXPECTED_ERROR] Failed to fetch gold price from GoldPricez: {}",
                    requestId, e.getMessage(), e);
            throw new GoldApiUnavailableException(
                    "Failed to fetch gold price from GoldPricez: " + e.getMessage(),
                    502,
                    "UNEXPECTED_ERROR",
                    requestId,
                    e
            );
        } finally {
            MDC.remove("requestId");
        }
    }

    /**
     * Unwraps a JSON string that may be double-encoded.
     * GoldPricez sometimes returns: "{\"ounce_price_usd\":\"4895.440\"}"
     * instead of: {"ounce_price_usd":"4895.440"}
     */
    private String unwrapJsonString(String responseBody, String requestId) {
        String trimmed = responseBody.trim();
        
        // Check if response is a JSON string (starts and ends with quotes)
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            try {
                // Use Jackson to properly unescape the JSON string
                String unwrapped = objectMapper.readValue(trimmed, String.class);
                log.debug("[requestId={}] Response was double-encoded, unwrapped successfully", requestId);
                return unwrapped;
            } catch (JsonProcessingException e) {
                log.warn("[requestId={}] Failed to unwrap JSON string, using original: {}", requestId, e.getMessage());
                return trimmed;
            }
        }
        
        // Response is already a proper JSON object
        return trimmed;
    }

    private String mapStatusToErrorType(int status) {
        if (status == 429) return "RATE_LIMITED";
        if (status == 403) return "FORBIDDEN";
        if (status == 401) return "UNAUTHORIZED";
        if (status >= 500) return "SERVER_ERROR";
        return "API_ERROR";
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
