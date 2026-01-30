package com.goldlens.client;

import com.goldlens.dto.GoldPriceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Component
public class GoldPriceClient {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceClient.class);

    private static final String SOURCE = "GoldAPI";
    private static final String CURRENCY = "USD";
    private static final String UNIT = "oz";

    private final WebClient webClient;
    private final String apiKey;

    public GoldPriceClient(@Value("${goldapi.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl("https://www.goldapi.io/api")
                .build();
    }

    /**
     * Fetches the current gold spot price (XAU/USD) from GoldAPI.
     * Returns empty if the API call fails or returns invalid data.
     */
    @SuppressWarnings("unchecked")
    public Optional<GoldPriceSnapshot> fetchCurrentPrice() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GoldAPI key not configured — cannot fetch gold price");
            return Optional.empty();
        }

        try {
            Map<String, Object> response = webClient.get()
                    .uri("/XAU/USD")
                    .header("x-access-token", apiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                log.warn("GoldAPI returned null response");
                return Optional.empty();
            }

            // Parse price
            Object priceObj = response.get("price");
            if (priceObj == null) {
                log.warn("GoldAPI response missing price field");
                return Optional.empty();
            }

            BigDecimal price = new BigDecimal(priceObj.toString());

            // Parse timestamp
            LocalDateTime asOf = LocalDateTime.now();
            Object timestampObj = response.get("timestamp");
            if (timestampObj != null) {
                try {
                    long timestamp = Long.parseLong(timestampObj.toString());
                    asOf = LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(timestamp),
                            ZoneId.systemDefault()
                    );
                } catch (NumberFormatException e) {
                    log.debug("Could not parse timestamp, using current time");
                }
            }

            GoldPriceSnapshot snapshot = GoldPriceSnapshot.builder()
                    .price(price)
                    .currency(CURRENCY)
                    .unit(UNIT)
                    .asOf(asOf)
                    .source(SOURCE)
                    .build();

            log.info("Fetched gold price: {} {}/{}", price, CURRENCY, UNIT);
            return Optional.of(snapshot);

        } catch (WebClientResponseException e) {
            log.warn("GoldAPI request failed: {} {}", e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to fetch gold price: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches the gold price for a specific historical date.
     * Returns empty if the API call fails, date is non-trading, or data unavailable.
     */
    @SuppressWarnings("unchecked")
    public Optional<HistoricalGoldPrice> fetchPriceForDate(LocalDate date) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GoldAPI key not configured — cannot fetch historical gold price");
            return Optional.empty();
        }

        String dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE); // YYYYMMDD format

        try {
            Map<String, Object> response = webClient.get()
                    .uri("/XAU/USD/" + dateStr)
                    .header("x-access-token", apiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                log.debug("GoldAPI returned null for date {}", date);
                return Optional.empty();
            }

            // Check for error response
            Object errorObj = response.get("error");
            if (errorObj != null && !errorObj.toString().isBlank()) {
                log.debug("GoldAPI error for date {}: {}", date, errorObj);
                return Optional.empty();
            }

            // Parse price
            Object priceObj = response.get("price");
            if (priceObj == null) {
                log.debug("GoldAPI response missing price for date {}", date);
                return Optional.empty();
            }

            BigDecimal price = new BigDecimal(priceObj.toString());

            log.debug("Fetched gold price for {}: {} USD/oz", date, price);
            return Optional.of(new HistoricalGoldPrice(date, price));

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404 || e.getStatusCode().value() == 400) {
                // Non-trading day or no data available
                log.debug("No gold price available for date {} ({})", date, e.getStatusCode());
            } else {
                log.warn("GoldAPI request failed for date {}: {} {}", date, e.getStatusCode(), e.getMessage());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to fetch gold price for date {}: {}", date, e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public record HistoricalGoldPrice(LocalDate date, BigDecimal price) {}
}
