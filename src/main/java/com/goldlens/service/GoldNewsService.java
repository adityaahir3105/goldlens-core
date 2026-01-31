package com.goldlens.service;

import com.goldlens.client.GNewsClient;
import com.goldlens.client.NewsApiClient;
import com.goldlens.dto.GoldNewsItem;
import com.goldlens.dto.GoldNewsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GoldNewsService {

    private static final Logger log = LoggerFactory.getLogger(GoldNewsService.class);

    private static final int MIN_VALID_ARTICLES = 3;
    private static final int MAX_ARTICLES_TO_RETURN = 6;

    // Keywords that indicate article is relevant to gold
    private static final Set<String> RELEVANCE_KEYWORDS = Set.of(
            "gold", "precious metal", "bullion", "xau",
            "federal reserve", "fed ", "interest rate", "rate cut", "rate hike",
            "inflation", "cpi", "pce",
            "treasury", "yield", "bond",
            "dollar", "dxy", "usd",
            "central bank", "monetary policy",
            "recession", "economic", "macro",
            "geopolitical", "safe haven", "safe-haven"
    );

    // Keywords that indicate article should be EXCLUDED
    private static final Set<String> EXCLUSION_KEYWORDS = Set.of(
            "gaming", "video game", "playstation", "xbox", "nintendo",
            "tech stock", "technology stock", "software",
            "earnings call", "quarterly earnings", "q1 earnings", "q2 earnings", "q3 earnings", "q4 earnings",
            "iphone", "android", "smartphone",
            "netflix", "streaming", "entertainment",
            "sports", "nfl", "nba", "football", "basketball",
            "celebrity", "lifestyle", "fashion",
            "crypto", "bitcoin", "ethereum", "cryptocurrency"
    );

    private static final Set<String> BULLISH_KEYWORDS = Set.of(
            "rate cut", "rate cuts", "cutting rates", "lower rates", "dovish",
            "inflation cooling", "inflation easing", "inflation falls", "inflation slows",
            "dollar weakness", "dollar weakens", "weak dollar", "dollar falls", "dollar drops",
            "geopolitical tension", "geopolitical risk", "war", "conflict", "crisis",
            "central bank buying", "gold reserves", "gold buying", "gold demand",
            "safe haven", "safe-haven", "uncertainty", "recession fears", "recession risk",
            "gold rises", "gold gains", "gold surges", "gold rallies", "gold hits"
    );

    private static final Set<String> BEARISH_KEYWORDS = Set.of(
            "rate hike", "rate hikes", "raising rates", "higher rates", "hawkish",
            "strong dollar", "dollar strength", "dollar rises", "dollar gains", "dollar rallies",
            "yields rising", "yields rise", "treasury yields", "yields surge",
            "inflation sticky", "inflation persistent", "inflation hot", "inflation rises",
            "tightening", "quantitative tightening",
            "gold falls", "gold drops", "gold declines", "gold slumps"
    );

    private final NewsApiClient newsApiClient;
    private final GNewsClient gNewsClient;

    public GoldNewsService(NewsApiClient newsApiClient, GNewsClient gNewsClient) {
        this.newsApiClient = newsApiClient;
        this.gNewsClient = gNewsClient;
    }

    public GoldNewsResponse getGoldNews() {
        String provider = null;
        List<GoldNewsItem> items = Collections.emptyList();

        // Try primary provider (NewsAPI)
        if (newsApiClient.isConfigured()) {
            Optional<List<GoldNewsItem>> result = newsApiClient.fetchGoldNews();
            if (result.isPresent() && !result.get().isEmpty()) {
                items = result.get();
                provider = newsApiClient.getProviderName();
                log.info("[GoldNews] Fetched {} raw articles from primary provider", items.size());
            }
        }

        // Fallback to GNews if primary failed
        if (items.isEmpty() && gNewsClient.isConfigured()) {
            log.info("[GoldNews] Primary provider failed or empty, trying fallback");
            Optional<List<GoldNewsItem>> result = gNewsClient.fetchGoldNews();
            if (result.isPresent() && !result.get().isEmpty()) {
                items = result.get();
                provider = gNewsClient.getProviderName();
                log.info("[GoldNews] Fetched {} raw articles from fallback provider", items.size());
            }
        }

        // STRICT RELEVANCE FILTERING - Remove unrelated articles
        List<GoldNewsItem> filteredItems = items.stream()
                .filter(this::isRelevantToGold)
                .limit(MAX_ARTICLES_TO_RETURN)
                .collect(Collectors.toList());

        log.info("[GoldNews] After relevance filtering: {} of {} articles kept", 
                filteredItems.size(), items.size());

        // If fewer than MIN_VALID_ARTICLES remain, return empty (don't pad with junk)
        if (filteredItems.size() < MIN_VALID_ARTICLES) {
            log.warn("[GoldNews] Only {} relevant articles found (min: {}), returning empty list",
                    filteredItems.size(), MIN_VALID_ARTICLES);
            return GoldNewsResponse.builder()
                    .items(Collections.emptyList())
                    .provider("none")
                    .fetchedAt(Instant.now())
                    .build();
        }

        // Apply sentiment analysis to filtered items
        filteredItems.forEach(this::applySentiment);

        return GoldNewsResponse.builder()
                .items(filteredItems)
                .provider(provider)
                .fetchedAt(Instant.now())
                .build();
    }

    /**
     * Validates that an article is relevant to gold and macro factors.
     * Returns false if article should be excluded.
     */
    private boolean isRelevantToGold(GoldNewsItem item) {
        String title = item.getTitle().toLowerCase();
        
        // First check exclusions - reject if any exclusion keyword found
        for (String exclusion : EXCLUSION_KEYWORDS) {
            if (title.contains(exclusion)) {
                log.debug("[GoldNews] Excluded article (matched '{}'): {}", exclusion, item.getTitle());
                return false;
            }
        }

        // Then check relevance - must contain at least one relevance keyword
        for (String keyword : RELEVANCE_KEYWORDS) {
            if (title.contains(keyword)) {
                return true;
            }
        }

        log.debug("[GoldNews] Excluded article (no relevance keywords): {}", item.getTitle());
        return false;
    }

    private void applySentiment(GoldNewsItem item) {
        String title = item.getTitle().toLowerCase();
        
        // Check for bullish keywords
        for (String keyword : BULLISH_KEYWORDS) {
            if (title.contains(keyword)) {
                item.setSentiment("BULLISH");
                return;
            }
        }

        // Check for bearish keywords
        for (String keyword : BEARISH_KEYWORDS) {
            if (title.contains(keyword)) {
                item.setSentiment("BEARISH");
                return;
            }
        }

        // Default to neutral
        item.setSentiment("NEUTRAL");
    }
}
