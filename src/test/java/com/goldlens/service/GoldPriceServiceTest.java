package com.goldlens.service;

import com.goldlens.client.GoldApiClient;
import com.goldlens.domain.GoldPriceHistory;
import com.goldlens.dto.GoldPriceSnapshot;
import com.goldlens.exception.GoldApiUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoldPriceServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    private GoldApiClient api;
    private GoldPriceHistoryService history;
    private MutableClock clock;
    private GoldPriceService service;

    @BeforeEach
    void setUp() {
        api = mock(GoldApiClient.class);
        history = mock(GoldPriceHistoryService.class);
        clock = new MutableClock(TODAY.atTime(10, 0).toInstant(ZoneOffset.UTC));
        service = new GoldPriceService(api, history, clock);
    }

    private static GoldPriceHistory stored(LocalDate date, String price) {
        return GoldPriceHistory.builder().date(date).price(new BigDecimal(price)).source("GoldAPI").build();
    }

    private static GoldPriceSnapshot live(String price) {
        return GoldPriceSnapshot.builder().price(new BigDecimal(price)).currency("USD").unit("oz")
                .asOf(TODAY.atTime(10, 0)).source("GoldAPI").build();
    }

    private static GoldApiUnavailableException quotaExceeded() {
        return new GoldApiUnavailableException("403 Forbidden", 403, "API_ERROR", "r1");
    }

    @Test
    void servesTodaysStoredPriceWithoutCallingTheApi() {
        when(history.findLatest()).thenReturn(Optional.of(stored(TODAY, "4100.50")));

        GoldPriceSnapshot result = service.getLatestPriceWithFallback();

        assertEquals(new BigDecimal("4100.50"), result.getPrice());
        assertFalse(result.isLive());
        verify(api, never()).fetchLatestGoldPrice();
    }

    @Test
    void fetchesOncePerDayAndStoresTheResult() {
        when(history.findLatest()).thenReturn(Optional.of(stored(TODAY.minusDays(1), "4000")));
        when(api.fetchLatestGoldPrice()).thenReturn(live("4123.45"));

        GoldPriceSnapshot first = service.getLatestPriceWithFallback();
        GoldPriceSnapshot second = service.getLatestPriceWithFallback();

        assertEquals(new BigDecimal("4123.45"), first.getPrice());
        assertTrue(first.isLive());
        assertEquals(first, second);
        verify(api, times(1)).fetchLatestGoldPrice();
        verify(history, times(1)).save(any(GoldPriceHistory.class));
    }

    @Test
    void fetchesAgainOnTheNextDay() {
        when(history.findLatest()).thenReturn(Optional.empty());
        when(api.fetchLatestGoldPrice()).thenReturn(live("4123.45"),
                GoldPriceSnapshot.builder().price(new BigDecimal("4200")).asOf(TODAY.plusDays(1).atTime(9, 0)).build());

        service.getLatestPriceWithFallback();
        clock.set(TODAY.plusDays(1).atTime(9, 0).toInstant(ZoneOffset.UTC));
        GoldPriceSnapshot nextDay = service.getLatestPriceWithFallback();

        assertEquals(new BigDecimal("4200"), nextDay.getPrice());
        verify(api, times(2)).fetchLatestGoldPrice();
    }

    @Test
    void onFailureServesLatestStoredPriceAndBacksOff() {
        when(history.findLatest()).thenReturn(Optional.of(stored(TODAY.minusDays(2), "3990")));
        when(api.fetchLatestGoldPrice()).thenThrow(quotaExceeded());

        GoldPriceSnapshot first = service.getLatestPriceWithFallback();
        clock.advanceHours(1);
        GoldPriceSnapshot second = service.getLatestPriceWithFallback();

        assertEquals(new BigDecimal("3990"), first.getPrice());
        assertFalse(first.isLive());
        assertEquals(new BigDecimal("3990"), second.getPrice());
        verify(api, times(1)).fetchLatestGoldPrice();
    }

    @Test
    void retriesAfterTheBackOffWindow() {
        when(history.findLatest()).thenReturn(Optional.of(stored(TODAY.minusDays(2), "3990")));
        when(api.fetchLatestGoldPrice()).thenThrow(quotaExceeded()).thenReturn(live("4050"));

        service.getLatestPriceWithFallback();
        clock.advanceHours(GoldPriceService.RETRY_AFTER_FAILURE.toHours() + 1);
        GoldPriceSnapshot later = service.getLatestPriceWithFallback();

        assertEquals(new BigDecimal("4050"), later.getPrice());
        verify(api, times(2)).fetchLatestGoldPrice();
    }

    @Test
    void failsWhenNothingIsStoredAndTheApiFails() {
        when(history.findLatest()).thenReturn(Optional.empty());
        when(api.fetchLatestGoldPrice()).thenThrow(quotaExceeded());

        assertThrows(GoldApiUnavailableException.class, service::getLatestPriceWithFallback);
        // Still in back-off: fails without another API call.
        assertThrows(GoldApiUnavailableException.class, service::getLatestPriceWithFallback);
        verify(api, times(1)).fetchLatestGoldPrice();
    }

    /** A clock the tests can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        void advanceHours(long hours) {
            now = now.plusSeconds(hours * 3600);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
