package com.goldlens.service;

import com.goldlens.client.GoldPricezClient;
import com.goldlens.config.CacheConfig;
import com.goldlens.dto.GoldPriceSnapshot;
import com.goldlens.exception.GoldApiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class GoldPriceService {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceService.class);
    private static final String CACHE_KEY = CacheConfig.GOLD_PRICE_CACHE_KEY;

    private final GoldPricezClient goldPricezClient;
    private final CacheManager cacheManager;

    public GoldPriceService(GoldPricezClient goldPricezClient, CacheManager cacheManager) {
        this.goldPricezClient = goldPricezClient;
        this.cacheManager = cacheManager;
    }

    /**
     * Returns the latest gold price with caching (45 sec TTL).
     * On API failure, returns cached value if available.
     */
    @Cacheable(value = CacheConfig.GOLD_PRICE_CACHE, key = "T(com.goldlens.config.CacheConfig).GOLD_PRICE_CACHE_KEY")
    public GoldPriceSnapshot getLatestPrice() {
        log.debug("Cache miss - fetching latest gold price from GoldPricez");
        return fetchLivePrice();
    }

    /**
     * Fetches live price, falls back to cache on failure.
     */
    public GoldPriceSnapshot getLatestPriceWithFallback() {
        try {
            GoldPriceSnapshot snapshot = fetchLivePrice();
            updateCache(snapshot);
            return snapshot;
        } catch (GoldApiUnavailableException e) {
            log.warn("[requestId={}] API failed, attempting cache fallback", e.getRequestId());
            GoldPriceSnapshot cached = getCachedPrice();
            if (cached != null) {
                log.info("Serving cached gold price due to API failure");
                cached.setLive(false);
                return cached;
            }
            throw e;
        }
    }

    private GoldPriceSnapshot fetchLivePrice() {
        GoldPriceSnapshot snapshot = goldPricezClient.fetchLatestGoldPrice();
        snapshot.setLive(true);
        snapshot.setSupportsHistory(false);
        return snapshot;
    }

    private void updateCache(GoldPriceSnapshot snapshot) {
        Cache cache = cacheManager.getCache(CacheConfig.GOLD_PRICE_CACHE);
        if (cache != null) {
            cache.put(CACHE_KEY, snapshot);
        }
    }

    private GoldPriceSnapshot getCachedPrice() {
        Cache cache = cacheManager.getCache(CacheConfig.GOLD_PRICE_CACHE);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(CACHE_KEY);
            if (wrapper != null) {
                return (GoldPriceSnapshot) wrapper.get();
            }
        }
        return null;
    }
}
