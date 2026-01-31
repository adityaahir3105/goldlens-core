package com.goldlens.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String GOLD_PRICE_CACHE = "goldPriceCache";
    public static final String GOLD_PRICE_CACHE_KEY = "gold.latest.usd.ounce";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(GOLD_PRICE_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(45, TimeUnit.SECONDS)
                .maximumSize(100));
        return cacheManager;
    }
}
