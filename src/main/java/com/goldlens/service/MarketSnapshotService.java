package com.goldlens.service;

import com.goldlens.domain.GoldPriceHistory;
import com.goldlens.domain.Indicator;
import com.goldlens.domain.IndicatorValue;
import com.goldlens.dto.MarketSnapshotDto;
import com.goldlens.dto.MarketSnapshotDto.MetricSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class MarketSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(MarketSnapshotService.class);

    private static final String REAL_YIELD_CODE = "US_10Y_REAL_YIELD";
    private static final String DXY_CODE = "US_DOLLAR_INDEX";
    private static final int STALE_THRESHOLD_DAYS = 2;

    private final GoldPriceHistoryService goldPriceHistoryService;
    private final IndicatorService indicatorService;
    private final IndicatorValueService indicatorValueService;

    public MarketSnapshotService(GoldPriceHistoryService goldPriceHistoryService,
                                  IndicatorService indicatorService,
                                  IndicatorValueService indicatorValueService) {
        this.goldPriceHistoryService = goldPriceHistoryService;
        this.indicatorService = indicatorService;
        this.indicatorValueService = indicatorValueService;
    }

    public MarketSnapshotDto getMarketSnapshot(int days) {
        LocalDate today = LocalDate.now();
        LocalDate staleThreshold = today.minusDays(STALE_THRESHOLD_DAYS);

        return MarketSnapshotDto.builder()
                .gold(buildGoldSnapshot(days, staleThreshold))
                .realYield(buildIndicatorSnapshot(REAL_YIELD_CODE, days, staleThreshold))
                .dxy(buildIndicatorSnapshot(DXY_CODE, days, staleThreshold))
                .build();
    }

    private MetricSnapshot buildGoldSnapshot(int days, LocalDate staleThreshold) {
        LocalDate sinceDate = LocalDate.now().minusDays(days);
        List<GoldPriceHistory> history = goldPriceHistoryService.findHistorySince(sinceDate);

        if (history.isEmpty()) {
            log.warn("No gold price history found for snapshot");
            return MetricSnapshot.builder()
                    .status("missing")
                    .fresh(false)
                    .build();
        }

        GoldPriceHistory latest = history.get(history.size() - 1);
        GoldPriceHistory oldest = history.get(0);

        BigDecimal change = null;
        BigDecimal changePercent = null;
        if (history.size() >= 2 && oldest.getPrice().compareTo(BigDecimal.ZERO) != 0) {
            change = latest.getPrice().subtract(oldest.getPrice());
            changePercent = change.divide(oldest.getPrice(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        boolean isFresh = !latest.getDate().isBefore(staleThreshold);
        String status = isFresh ? "fresh" : "stale";

        return MetricSnapshot.builder()
                .value(latest.getPrice())
                .change(change)
                .changePercent(changePercent)
                .asOfDate(latest.getDate())
                .source(latest.getSource())
                .fresh(isFresh)
                .status(status)
                .build();
    }

    private MetricSnapshot buildIndicatorSnapshot(String indicatorCode, int days, LocalDate staleThreshold) {
        Optional<Indicator> indicatorOpt = indicatorService.findByCode(indicatorCode);

        if (indicatorOpt.isEmpty()) {
            log.warn("Indicator not found: {}", indicatorCode);
            return MetricSnapshot.builder()
                    .status("missing")
                    .fresh(false)
                    .build();
        }

        Indicator indicator = indicatorOpt.get();
        LocalDate sinceDate = LocalDate.now().minusDays(days);
        List<IndicatorValue> history = indicatorValueService.findHistorySince(indicator, sinceDate);

        if (history.isEmpty()) {
            log.warn("No history found for indicator: {}", indicatorCode);
            return MetricSnapshot.builder()
                    .status("missing")
                    .fresh(false)
                    .build();
        }

        IndicatorValue latest = history.get(history.size() - 1);
        IndicatorValue oldest = history.get(0);

        BigDecimal change = null;
        BigDecimal changePercent = null;
        if (history.size() >= 2 && oldest.getValue().compareTo(BigDecimal.ZERO) != 0) {
            change = latest.getValue().subtract(oldest.getValue());
            changePercent = change.divide(oldest.getValue(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        boolean isFresh = !latest.getDate().isBefore(staleThreshold);
        String status = isFresh ? "fresh" : "stale";

        if (!isFresh) {
            log.warn("Indicator {} is stale: last updated {}", indicatorCode, latest.getDate());
        }

        return MetricSnapshot.builder()
                .value(latest.getValue())
                .change(change)
                .changePercent(changePercent)
                .asOfDate(latest.getDate())
                .source(latest.getSource())
                .fresh(isFresh)
                .status(status)
                .build();
    }
}
