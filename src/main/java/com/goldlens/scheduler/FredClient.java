package com.goldlens.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class FredClient {

    private static final Logger log = LoggerFactory.getLogger(FredClient.class);

    private final WebClient webClient;
    private final String apiKey;

    public FredClient(WebClient fredWebClient, @Qualifier("fredApiKey") String fredApiKey) {
        this.webClient = fredWebClient;
        this.apiKey = fredApiKey;
    }

    /**
     * Fetches the latest observation for a given FRED series.
     * Returns empty if the API call fails or no valid observation exists.
     */
    @SuppressWarnings("unchecked")
    public Optional<FredObservation> fetchLatestObservation(String seriesId) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/series/observations")
                            .queryParam("series_id", seriesId)
                            .queryParam("api_key", apiKey)
                            .queryParam("file_type", "json")
                            .queryParam("sort_order", "desc")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("observations")) {
                log.warn("FRED API returned no observations for series: {}", seriesId);
                return Optional.empty();
            }

            List<Map<String, String>> observations = (List<Map<String, String>>) response.get("observations");
            if (observations == null || observations.isEmpty()) {
                log.warn("FRED API returned empty observations array for series: {}", seriesId);
                return Optional.empty();
            }

            Map<String, String> latest = observations.get(0);
            String dateStr = latest.get("date");
            String valueStr = latest.get("value");

            // FRED uses "." for missing values
            if (".".equals(valueStr) || valueStr == null || valueStr.isBlank()) {
                log.warn("FRED returned missing value for series: {} on date: {}", seriesId, dateStr);
                return Optional.empty();
            }

            LocalDate date = LocalDate.parse(dateStr);
            BigDecimal value = new BigDecimal(valueStr);

            return Optional.of(new FredObservation(date, value));

        } catch (WebClientResponseException e) {
            log.error("FRED API request failed for series {}: {} - {}", seriesId, e.getStatusCode(), e.getStatusText());
            log.error("FRED API error response body: {}", e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch FRED data for series {}: {}", seriesId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Fetches historical observations for a given FRED series.
     * Returns a list of valid observations, skipping missing values.
     */
    @SuppressWarnings("unchecked")
    public List<FredObservation> fetchHistoricalObservations(String seriesId, LocalDate startDate, int limit) {
        log.info("Fetching FRED historical data: series={}, start={}, limit={}, api_key={}***",
                seriesId, startDate, limit, apiKey != null ? apiKey.substring(0, Math.min(4, apiKey.length())) : "NULL");

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/series/observations")
                            .queryParam("series_id", seriesId)
                            .queryParam("api_key", apiKey)
                            .queryParam("file_type", "json")
                            .queryParam("observation_start", startDate.toString())
                            .queryParam("sort_order", "asc")
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("observations")) {
                log.warn("FRED API returned no observations for series: {}", seriesId);
                return List.of();
            }

            List<Map<String, String>> observations = (List<Map<String, String>>) response.get("observations");
            if (observations == null || observations.isEmpty()) {
                log.warn("FRED API returned empty observations array for series: {}", seriesId);
                return List.of();
            }

            int totalFetched = observations.size();
            List<FredObservation> validObservations = observations.stream()
                    .filter(obs -> isValidObservation(obs))
                    .map(obs -> new FredObservation(
                            LocalDate.parse(obs.get("date")),
                            new BigDecimal(obs.get("value"))))
                    .toList();

            int skippedCount = totalFetched - validObservations.size();
            log.info("FRED series {}: fetched {} observations, {} valid, {} skipped (missing values)",
                    seriesId, totalFetched, validObservations.size(), skippedCount);

            return validObservations;

        } catch (WebClientResponseException e) {
            log.error("FRED API request failed for series {}: {} - {}", seriesId, e.getStatusCode(), e.getStatusText());
            log.error("FRED API error response body: {}", e.getResponseBodyAsString());
            log.error("Request URL: /series/observations?series_id={}&observation_start={}&sort_order=asc&limit={}",
                    seriesId, startDate, limit);
            return List.of();
        } catch (Exception e) {
            log.error("Failed to fetch FRED historical data for series {}: {}", seriesId, e.getMessage(), e);
            return List.of();
        }
    }

    private boolean isValidObservation(Map<String, String> obs) {
        String dateStr = obs.get("date");
        String valueStr = obs.get("value");

        if (dateStr == null || dateStr.isBlank()) {
            return false;
        }
        if (valueStr == null || valueStr.isBlank() || ".".equals(valueStr)) {
            return false;
        }
        return true;
    }

    /**
     * Searches FRED for series matching the given search text.
     * Returns a list of series IDs that match the search criteria.
     */
    @SuppressWarnings("unchecked")
    public List<FredSeriesInfo> searchSeries(String searchText, int limit) {
        log.info("Searching FRED for series: searchText='{}', limit={}", searchText, limit);

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/series/search")
                            .queryParam("search_text", searchText)
                            .queryParam("api_key", apiKey)
                            .queryParam("file_type", "json")
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("seriess")) {
                log.warn("FRED search returned no results for: {}", searchText);
                return List.of();
            }

            List<Map<String, Object>> seriesList = (List<Map<String, Object>>) response.get("seriess");
            if (seriesList == null || seriesList.isEmpty()) {
                log.warn("FRED search returned empty series array for: {}", searchText);
                return List.of();
            }

            List<FredSeriesInfo> results = seriesList.stream()
                    .map(s -> new FredSeriesInfo(
                            (String) s.get("id"),
                            (String) s.get("title"),
                            (String) s.get("units"),
                            (String) s.get("frequency")))
                    .toList();

            log.info("FRED search found {} series for '{}'", results.size(), searchText);
            return results;

        } catch (WebClientResponseException e) {
            log.error("FRED search API failed: {} - {}", e.getStatusCode(), e.getStatusText());
            log.error("FRED search error response: {}", e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.error("Failed to search FRED: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Finds a valid gold price series from FRED by searching and validating.
     * Returns the first series that has valid observations.
     */
    public Optional<String> findValidGoldPriceSeries() {
        // Search for gold price series
        List<FredSeriesInfo> candidates = searchSeries("gold price USD", 20);

        if (candidates.isEmpty()) {
            log.warn("No gold price series found via FRED search");
            return Optional.empty();
        }

        // Filter for likely gold price series (contains price, USD, gold in title)
        List<FredSeriesInfo> filtered = candidates.stream()
                .filter(s -> s.title() != null &&
                        s.title().toLowerCase().contains("gold") &&
                        (s.units() == null || s.units().toLowerCase().contains("dollar") ||
                                s.units().toLowerCase().contains("usd") ||
                                s.title().toLowerCase().contains("price")))
                .toList();

        if (filtered.isEmpty()) {
            filtered = candidates; // Fall back to all candidates
        }

        log.info("Filtered to {} candidate gold price series", filtered.size());

        // Try each candidate until we find one with valid observations
        for (FredSeriesInfo series : filtered) {
            log.info("Trying series: {} - {}", series.id(), series.title());

            List<FredObservation> testObs = fetchHistoricalObservations(
                    series.id(), LocalDate.now().minusDays(30), 5);

            if (!testObs.isEmpty()) {
                log.info("Found valid gold price series: {} - {} ({} test observations)",
                        series.id(), series.title(), testObs.size());
                return Optional.of(series.id());
            }

            log.info("Series {} has no valid observations, trying next", series.id());
        }

        log.warn("No valid gold price series found after testing {} candidates", filtered.size());
        return Optional.empty();
    }

    public record FredObservation(LocalDate date, BigDecimal value) {}

    public record FredSeriesInfo(String id, String title, String units, String frequency) {}
}
