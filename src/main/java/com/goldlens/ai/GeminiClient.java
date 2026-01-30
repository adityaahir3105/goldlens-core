package com.goldlens.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String SYSTEM_INSTRUCTION = """
            You are a financial education assistant.
            Explain concepts clearly and neutrally.
            Do NOT give investment advice.
            Do NOT recommend buying or selling.
            Do NOT predict prices.""";

    private final WebClient webClient;
    private final String apiKey;

    public GeminiClient(@Value("${gemini.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl(GEMINI_API_URL)
                .build();
    }

    /**
     * Sends a prompt to Gemini and returns the text response.
     * Returns empty if the API call fails.
     */
    @SuppressWarnings("unchecked")
    public Optional<String> generateContent(String prompt) {
        try {
            Map<String, Object> requestBody = buildRequestBody(prompt);

            Map<String, Object> response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null) {
                log.warn("Gemini API returned null response");
                return Optional.empty();
            }

            return extractTextFromResponse(response);

        } catch (WebClientResponseException e) {
            log.warn("Gemini API request failed: {} {}", e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to call Gemini API: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "maxOutputTokens", 300,
                        "temperature", 0.3
                )
        );
    }

    @SuppressWarnings("unchecked")
    private Optional<String> extractTextFromResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                log.warn("Gemini response has no candidates");
                return Optional.empty();
            }

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            if (content == null) {
                return Optional.empty();
            }

            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                return Optional.empty();
            }

            String text = (String) parts.get(0).get("text");
            return Optional.ofNullable(text);

        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
