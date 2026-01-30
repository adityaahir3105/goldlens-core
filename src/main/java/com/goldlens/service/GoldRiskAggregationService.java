package com.goldlens.service;

import com.goldlens.domain.GoldRiskSnapshot;
import com.goldlens.domain.Indicator;
import com.goldlens.domain.RiskLevel;
import com.goldlens.domain.Signal;
import com.goldlens.domain.SignalType;
import com.goldlens.repository.GoldRiskSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class GoldRiskAggregationService {

    private static final Logger log = LoggerFactory.getLogger(GoldRiskAggregationService.class);

    private static final String REAL_YIELD_CODE = "US_10Y_REAL_YIELD";
    private static final String DXY_CODE = "US_DOLLAR_INDEX";

    private final GoldRiskSnapshotRepository goldRiskSnapshotRepository;
    private final IndicatorService indicatorService;
    private final SignalEngineService signalEngineService;

    public GoldRiskAggregationService(GoldRiskSnapshotRepository goldRiskSnapshotRepository,
                                      IndicatorService indicatorService,
                                      SignalEngineService signalEngineService) {
        this.goldRiskSnapshotRepository = goldRiskSnapshotRepository;
        this.indicatorService = indicatorService;
        this.signalEngineService = signalEngineService;
    }

    /**
     * Computes and stores the aggregated gold risk snapshot for the given date.
     * Idempotent: skips if snapshot already exists for that date.
     */
    public void computeAndStoreRiskSnapshot(LocalDate asOfDate) {
        log.info("Computing gold risk snapshot for date {}", asOfDate);

        if (goldRiskSnapshotRepository.existsByAsOfDate(asOfDate)) {
            log.info("Gold risk snapshot already exists for date {} – skipping", asOfDate);
            return;
        }

        Optional<Signal> realYieldSignal = getLatestSignal(REAL_YIELD_CODE);
        Optional<Signal> dxySignal = getLatestSignal(DXY_CODE);

        RiskResult result = aggregateRisk(realYieldSignal, dxySignal);
        log.info("Gold risk computed: level={}, reason={}", result.level(), result.reason());

        GoldRiskSnapshot snapshot = GoldRiskSnapshot.builder()
                .riskLevel(result.level())
                .reason(result.reason())
                .asOfDate(asOfDate)
                .build();

        goldRiskSnapshotRepository.save(snapshot);
        log.info("Inserted gold risk snapshot for date {}", asOfDate);
    }

    private Optional<Signal> getLatestSignal(String indicatorCode) {
        return indicatorService.findByCode(indicatorCode)
                .flatMap(signalEngineService::findLatestByIndicator);
    }

    /**
     * Aggregates risk from multiple signals using deterministic rules.
     */
    private RiskResult aggregateRisk(Optional<Signal> realYieldSignal, Optional<Signal> dxySignal) {
        // If any signal is missing, return MEDIUM with explanation
        if (realYieldSignal.isEmpty() || dxySignal.isEmpty()) {
            return new RiskResult(
                    RiskLevel.MEDIUM,
                    "Incomplete data – not all indicator signals are available"
            );
        }

        SignalType yieldType = realYieldSignal.get().getSignalType();
        SignalType dxyType = dxySignal.get().getSignalType();

        // Both RED → HIGH
        if (yieldType == SignalType.RED && dxyType == SignalType.RED) {
            return new RiskResult(
                    RiskLevel.HIGH,
                    "Rising real yields and a strengthening dollar increase downside risk for gold"
            );
        }

        // Both GREEN → LOW
        if (yieldType == SignalType.GREEN && dxyType == SignalType.GREEN) {
            return new RiskResult(
                    RiskLevel.LOW,
                    "Easing real yields and a weakening dollar are supportive for gold"
            );
        }

        // One RED + one YELLOW → MEDIUM
        if ((yieldType == SignalType.RED && dxyType == SignalType.YELLOW) ||
            (yieldType == SignalType.YELLOW && dxyType == SignalType.RED)) {
            return new RiskResult(
                    RiskLevel.MEDIUM,
                    buildMixedReason(yieldType, dxyType)
            );
        }

        // One RED + one GREEN → MEDIUM
        if ((yieldType == SignalType.RED && dxyType == SignalType.GREEN) ||
            (yieldType == SignalType.GREEN && dxyType == SignalType.RED)) {
            return new RiskResult(
                    RiskLevel.MEDIUM,
                    "Mixed signals – one indicator is bearish while the other is supportive for gold"
            );
        }

        // Both YELLOW → MEDIUM
        if (yieldType == SignalType.YELLOW && dxyType == SignalType.YELLOW) {
            return new RiskResult(
                    RiskLevel.MEDIUM,
                    "Both indicators show mixed trends – uncertain outlook for gold"
            );
        }

        // One GREEN + one YELLOW → MEDIUM (leaning positive but not confirmed)
        return new RiskResult(
                RiskLevel.MEDIUM,
                "Partially supportive conditions – one indicator is positive while the other is mixed"
        );
    }

    private String buildMixedReason(SignalType yieldType, SignalType dxyType) {
        if (yieldType == SignalType.RED) {
            return "Rising real yields are negative for gold, while dollar trends are mixed";
        } else {
            return "A strengthening dollar pressures gold, while real yield trends are mixed";
        }
    }

    public Optional<GoldRiskSnapshot> findLatest() {
        return goldRiskSnapshotRepository.findTopByOrderByAsOfDateDesc();
    }

    private record RiskResult(RiskLevel level, String reason) {}
}
