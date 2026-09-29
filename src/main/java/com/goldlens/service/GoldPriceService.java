package com.goldlens.service;

import com.goldlens.client.GoldApiClient;
import com.goldlens.domain.GoldPriceHistory;
import com.goldlens.dto.GoldPriceSnapshot;
import com.goldlens.exception.GoldApiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Serves the latest gold price while spending as few GoldAPI calls as possible
 * (the free plan allows 100 requests per month).
 *
 * Order of preference:
 *  1. The price already fetched today (kept in memory, or stored in gold_price_history).
 *  2. One live GoldAPI call, whose result is stored as today's price.
 *  3. The most recent stored price, marked as not live.
 *
 * After a failed GoldAPI call no new attempt is made for {@link #RETRY_AFTER_FAILURE},
 * so an exhausted quota doesn't turn every page view into another rejected call.
 */
@Service
public class GoldPriceService {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceService.class);

    static final Duration RETRY_AFTER_FAILURE = Duration.ofHours(6);
    private static final String SOURCE = "GoldAPI";

    private final GoldApiClient goldApiClient;
    private final GoldPriceHistoryService goldPriceHistoryService;
    private final Clock clock;

    private GoldPriceSnapshot todaysLiveSnapshot;
    private Instant lastFailedAttempt;

    @Autowired
    public GoldPriceService(GoldApiClient goldApiClient, GoldPriceHistoryService goldPriceHistoryService) {
        this(goldApiClient, goldPriceHistoryService, Clock.systemDefaultZone());
    }

    GoldPriceService(GoldApiClient goldApiClient, GoldPriceHistoryService goldPriceHistoryService, Clock clock) {
        this.goldApiClient = goldApiClient;
        this.goldPriceHistoryService = goldPriceHistoryService;
        this.clock = clock;
    }

    // synchronized: the dashboard requests this several times per page load, and
    // concurrent cache misses must not each spend a GoldAPI call.
    public synchronized GoldPriceSnapshot getLatestPriceWithFallback() {
        LocalDate today = LocalDate.now(clock);

        if (todaysLiveSnapshot != null && todaysLiveSnapshot.getAsOf().toLocalDate().equals(today)) {
            return todaysLiveSnapshot;
        }

        Optional<GoldPriceHistory> stored = goldPriceHistoryService.findLatest();
        if (stored.isPresent() && stored.get().getDate().equals(today)) {
            return fromHistory(stored.get());
        }

        GoldApiUnavailableException failure = null;
        if (lastFailedAttempt == null || lastFailedAttempt.plus(RETRY_AFTER_FAILURE).isBefore(clock.instant())) {
            try {
                GoldPriceSnapshot snapshot = goldApiClient.fetchLatestGoldPrice();
                snapshot.setLive(true);
                snapshot.setSupportsHistory(false);
                if (snapshot.getAsOf() == null) {
                    snapshot.setAsOf(LocalDateTime.now(clock));
                }
                storeAsToday(snapshot, today);
                todaysLiveSnapshot = snapshot;
                lastFailedAttempt = null;
                return snapshot;
            } catch (GoldApiUnavailableException e) {
                lastFailedAttempt = clock.instant();
                failure = e;
                log.warn("[requestId={}] GoldAPI failed ({}); not retrying for {}",
                        e.getRequestId(), e.getMessage(), RETRY_AFTER_FAILURE);
            }
        }

        if (stored.isPresent()) {
            return fromHistory(stored.get());
        }
        if (failure != null) {
            throw failure;
        }
        throw new GoldApiUnavailableException(
                "No stored gold price and GoldAPI is in back-off after a recent failure",
                503, "BACKOFF", "backoff");
    }

    private void storeAsToday(GoldPriceSnapshot snapshot, LocalDate today) {
        if (goldPriceHistoryService.existsByDate(today)) {
            return;
        }
        try {
            goldPriceHistoryService.save(GoldPriceHistory.builder()
                    .date(today)
                    .price(snapshot.getPrice())
                    .source(SOURCE)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Another writer (the scheduler or a backfill) stored today's price first.
            log.debug("Gold price for {} already stored", today);
        }
    }

    private GoldPriceSnapshot fromHistory(GoldPriceHistory history) {
        return GoldPriceSnapshot.builder()
                .price(history.getPrice())
                .currency("USD")
                .unit("oz")
                .asOf(history.getDate().atStartOfDay())
                .source(history.getSource())
                .isLive(false)
                .supportsHistory(false)
                .build();
    }
}
