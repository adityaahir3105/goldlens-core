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

    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String METADATA_TOKEN_URL =
            "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String SYSTEM_INSTRUCTION = """
            You are a financial education assistant.
            Explain concepts clearly and neutrally.
            Do NOT give investment advice.
            Do NOT recommend buying or selling.
            Do NOT predict prices.""";

    private final WebClient webClient = WebClient.builder().build();
    private final String apiKey;
    private final String url;
    private final boolean vertex;

    private volatile String accessToken = "";
    private volatile long accessTokenExpiresAtMs = 0;

    /**
     * With gemini.backend=vertex the call goes to Vertex AI as the Cloud Run service account, so
     * usage bills the Google Cloud project; otherwise gemini.api.key goes to the Gemini Developer API.
     */
    public GeminiClient(@Value("${gemini.api.key}") String apiKey,
                        @Value("${gemini.model:gemini-3.8-flash}") String model,
                        @Value("${gemini.backend:}") String backend,
                        @Value("${gemini.project:}") String project,
                        @Value("${gemini.location:global}") String location) {
        this.apiKey = apiKey;
        this.vertex = "vertex".equalsIgnoreCase(backend);
        if (vertex) {
            String host = "global".equals(location) ? "aiplatform.googleapis.com" : location + "-aiplatform.googleapis.com";
            this.url = "https://" + host + "/v1/projects/" + project + "/locations/" + location
                    + "/publishers/google/models/" + model + ":generateContent";
        } else {
            this.url = GEMINI_BASE_URL + model + ":generateContent";
        }
    }

    /**
     * Sends a prompt to Gemini and returns the text response.
     * Returns empty if the API call fails.
     */
    @SuppressWarnings("unchecked")
    public Optional<String> generateContent(String prompt) {
        try {
            Map<String, Object> requestBody = buildRequestBody(prompt);

            WebClient.RequestBodySpec request = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON);
            request = vertex
                    ? request.headers(h -> h.setBearerAuth(vertexAccessToken()))
                    : request.header("x-goog-api-key", apiKey);

            Map<String, Object> response = request
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
            String body = e.getResponseBodyAsString();
            log.warn("Gemini API request failed: {} {}", e.getStatusCode(),
                    body.length() > 200 ? body.substring(0, 200) : body);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to call Gemini API: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Access token for the Cloud Run service account, from the metadata server, cached until near expiry. */
    @SuppressWarnings("unchecked")
    private synchronized String vertexAccessToken() {
        if (!accessToken.isEmpty() && accessTokenExpiresAtMs > System.currentTimeMillis() + 60_000) {
            return accessToken;
        }
        Map<String, Object> body = webClient.get()
                .uri(METADATA_TOKEN_URL)
                .header("Metadata-Flavor", "Google")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .block();
        if (body == null || !(body.get("access_token") instanceof String token)) {
            throw new IllegalStateException("Metadata server returned no access token");
        }
        long expiresIn = body.get("expires_in") instanceof Number n ? n.longValue() : 300;
        accessToken = token;
        accessTokenExpiresAtMs = System.currentTimeMillis() + expiresIn * 1000;
        return token;
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))
                ),
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))
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
