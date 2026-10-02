package com.goldlens.service;

import com.goldlens.client.GNewsClient;
import com.goldlens.client.NewsApiClient;
import com.goldlens.client.OpenNewsClient;
import com.goldlens.dto.GoldNewsItem;
import com.goldlens.dto.GoldNewsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GoldNewsService {

    private static final Logger log = LoggerFactory.getLogger(GoldNewsService.class);

    // Show any relevant articles; below this many, GNews is asked to top up the list.
    private static final int TARGET_ARTICLES = 3;
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
    private final OpenNewsClient openNewsClient;

    public GoldNewsService(NewsApiClient newsApiClient, GNewsClient gNewsClient, OpenNewsClient openNewsClient) {
        this.newsApiClient = newsApiClient;
        this.gNewsClient = gNewsClient;
        this.openNewsClient = openNewsClient;
    }

    // Both providers have small free quotas (NewsAPI 100/day, GNews 100/day), and the
    // dashboard asks for news on every page view, so serve a cached response.
    static final Duration CACHE_TTL = Duration.ofMinutes(30);
    // An empty result (quota exhausted, provider down) is retried sooner.
    static final Duration EMPTY_CACHE_TTL = Duration.ofMinutes(10);

    private GoldNewsResponse cached;
    private Instant cachedUntil = Instant.MIN;

    public synchronized GoldNewsResponse getGoldNews() {
        Instant now = Instant.now();
        if (cached != null && now.isBefore(cachedUntil)) {
            return cached;
        }
        cached = fetchGoldNews();
        cachedUntil = now.plus(cached.getItems().isEmpty() ? EMPTY_CACHE_TTL : CACHE_TTL);
        return cached;
    }

    private GoldNewsResponse fetchGoldNews() {
        List<String> providers = new ArrayList<>();
        List<GoldNewsItem> relevant = new ArrayList<>();

        // Keyless sources first (GDELT and publisher RSS); the keyed providers below only
        // top up. The free NewsAPI and GNews plans are for development use, so production
        // should not depend on them.
        addRelevant(openNewsClient.fetchGoldNews(), openNewsClient.getProviderName(), relevant, providers);

        if (relevant.size() < TARGET_ARTICLES && newsApiClient.isConfigured()) {
            addRelevant(newsApiClient.fetchGoldNews(), newsApiClient.getProviderName(), relevant, providers);
        }

        // Top up from GNews when the others didn't yield enough relevant articles
        if (relevant.size() < TARGET_ARTICLES && gNewsClient.isConfigured()) {
            log.info("[GoldNews] {} relevant articles from primary, trying fallback", relevant.size());
            addRelevant(gNewsClient.fetchGoldNews(), gNewsClient.getProviderName(), relevant, providers);
        }

        List<GoldNewsItem> filteredItems = relevant.stream()
                .limit(MAX_ARTICLES_TO_RETURN)
                .collect(Collectors.toList());

        if (filteredItems.isEmpty()) {
            log.warn("[GoldNews] No relevant articles found, returning empty list");
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
                .provider(String.join("+", providers))
                .fetchedAt(Instant.now())
                .build();
    }

    /** Adds the provider's relevant articles, skipping URLs already collected. */
    private void addRelevant(Optional<List<GoldNewsItem>> result, String providerName,
                             List<GoldNewsItem> relevant, List<String> providers) {
        if (result.isEmpty() || result.get().isEmpty()) {
            return;
        }
        Set<String> seenUrls = relevant.stream().map(GoldNewsItem::getUrl).collect(Collectors.toSet());
        List<GoldNewsItem> kept = result.get().stream()
                .filter(this::isRelevantToGold)
                .filter(item -> item.getUrl() == null || !seenUrls.contains(item.getUrl()))
                .collect(Collectors.toList());
        log.info("[GoldNews] {}: kept {} of {} articles after relevance filtering",
                providerName, kept.size(), result.get().size());
        if (!kept.isEmpty()) {
            relevant.addAll(kept);
            providers.add(providerName);
        }
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
