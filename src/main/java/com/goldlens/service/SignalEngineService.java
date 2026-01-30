package com.goldlens.service;

import com.goldlens.domain.Indicator;
import com.goldlens.domain.IndicatorValue;
import com.goldlens.domain.Signal;
import com.goldlens.domain.SignalType;
import com.goldlens.repository.SignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class SignalEngineService {

    private static final Logger log = LoggerFactory.getLogger(SignalEngineService.class);

    private static final BigDecimal CONFIDENCE_HIGH = new BigDecimal("0.7");
    private static final BigDecimal CONFIDENCE_MEDIUM = new BigDecimal("0.5");

    // Indicator-specific reason messages
    private static final String REAL_YIELD_CODE = "US_10Y_REAL_YIELD";
    private static final String DXY_CODE = "US_DOLLAR_INDEX";

    private final SignalRepository signalRepository;
    private final IndicatorValueService indicatorValueService;

    public SignalEngineService(SignalRepository signalRepository, IndicatorValueService indicatorValueService) {
        this.signalRepository = signalRepository;
        this.indicatorValueService = indicatorValueService;
    }

    /**
     * Computes and persists a signal for the given indicator and date.
     * Idempotent: skips if signal already exists for that date.
     */
    public void computeAndStoreSignal(Indicator indicator, LocalDate asOfDate) {
        log.info("Evaluating signal for indicator={} asOfDate={}", indicator.getCode(), asOfDate);

        if (signalRepository.existsByIndicatorAndAsOfDate(indicator, asOfDate)) {
            log.info("Signal already exists for date {} – skipping", asOfDate);
            return;
        }

        List<IndicatorValue> recentValues = indicatorValueService.findRecentByIndicator(indicator, 3);

        if (recentValues.size() < 2) {
            log.info("Not enough data points ({}) to compute signal – skipping", recentValues.size());
            return;
        }

        SignalResult result = evaluateTrend(indicator.getCode(), recentValues);
        log.info("Signal evaluated: type={}, reason={}", result.type(), result.reason());

        Signal signal = Signal.builder()
                .indicator(indicator)
                .signalType(result.type())
                .reason(result.reason())
                .asOfDate(asOfDate)
                .confidence(result.confidence())
                .build();

        signalRepository.save(signal);
        log.info("Inserted signal for date {}", asOfDate);
    }

    /**
     * Evaluates trend direction from recent values.
     * Values are ordered most recent first.
     */
    private SignalResult evaluateTrend(String indicatorCode, List<IndicatorValue> values) {
        // Values are sorted by date descending (most recent first)
        // Compare consecutive pairs to determine trend direction
        int risingCount = 0;
        int fallingCount = 0;

        for (int i = 0; i < values.size() - 1; i++) {
            BigDecimal current = values.get(i).getValue();
            BigDecimal previous = values.get(i + 1).getValue();

            int comparison = current.compareTo(previous);
            if (comparison > 0) {
                risingCount++;
            } else if (comparison < 0) {
                fallingCount++;
            }
        }

        // Rising for 3 consecutive observations → RED
        if (risingCount >= 2 && values.size() >= 3) {
            return new SignalResult(SignalType.RED, getRedReason(indicatorCode), CONFIDENCE_HIGH);
        }

        // Falling for 2+ observations → GREEN
        if (fallingCount >= 2) {
            return new SignalResult(SignalType.GREEN, getGreenReason(indicatorCode), CONFIDENCE_HIGH);
        }

        // Mixed / flat → YELLOW
        return new SignalResult(SignalType.YELLOW, getYellowReason(indicatorCode), CONFIDENCE_MEDIUM);
    }

    private String getRedReason(String indicatorCode) {
        return switch (indicatorCode) {
            case REAL_YIELD_CODE -> "Real yields rising consistently – historically bearish for gold";
            case DXY_CODE -> "A strengthening dollar tends to pressure gold prices";
            default -> "Indicator rising consistently – negative for gold";
        };
    }

    private String getGreenReason(String indicatorCode) {
        return switch (indicatorCode) {
            case REAL_YIELD_CODE -> "Real yields easing – supportive for gold";
            case DXY_CODE -> "A weakening dollar supports gold prices";
            default -> "Indicator falling – supportive for gold";
        };
    }

    private String getYellowReason(String indicatorCode) {
        return switch (indicatorCode) {
            case REAL_YIELD_CODE -> "Real yields mixed – potential correction risk";
            case DXY_CODE -> "Dollar index mixed – uncertain impact on gold";
            default -> "Indicator mixed – uncertain outlook";
        };
    }

    public Optional<Signal> findLatestByIndicator(Indicator indicator) {
        return signalRepository.findTopByIndicatorOrderByAsOfDateDesc(indicator);
    }

    private record SignalResult(SignalType type, String reason, BigDecimal confidence) {}
}
