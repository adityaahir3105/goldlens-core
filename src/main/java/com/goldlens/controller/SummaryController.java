package com.goldlens.controller;

import com.goldlens.domain.GoldRiskSnapshot;
import com.goldlens.domain.Signal;
import com.goldlens.dto.WeeklySummaryDto;
import com.goldlens.service.GoldRiskAggregationService;
import com.goldlens.service.IndicatorService;
import com.goldlens.service.SignalEngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private static final String REAL_YIELD_CODE = "US_10Y_REAL_YIELD";
    private static final String DXY_CODE = "US_DOLLAR_INDEX";

    private final IndicatorService indicatorService;
    private final SignalEngineService signalEngineService;
    private final GoldRiskAggregationService goldRiskAggregationService;

    public SummaryController(IndicatorService indicatorService,
                             SignalEngineService signalEngineService,
                             GoldRiskAggregationService goldRiskAggregationService) {
        this.indicatorService = indicatorService;
        this.signalEngineService = signalEngineService;
        this.goldRiskAggregationService = goldRiskAggregationService;
    }

    @GetMapping("/weekly")
    public ResponseEntity<WeeklySummaryDto> getWeeklySummary() {
        Optional<GoldRiskSnapshot> snapshotOpt = goldRiskAggregationService.findLatest();
        if (snapshotOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        GoldRiskSnapshot snapshot = snapshotOpt.get();
        List<WeeklySummaryDto.IndicatorSignalSummary> indicatorSummaries = collectIndicatorSummaries();

        WeeklySummaryDto summary = WeeklySummaryDto.builder()
                .weekEnding(LocalDate.now())
                .goldRisk(WeeklySummaryDto.GoldRiskSummary.builder()
                        .riskLevel(snapshot.getRiskLevel().name())
                        .reason(snapshot.getReason())
                        .build())
                .indicators(indicatorSummaries)
                .build();

        return ResponseEntity.ok(summary);
    }

    private List<WeeklySummaryDto.IndicatorSignalSummary> collectIndicatorSummaries() {
        List<WeeklySummaryDto.IndicatorSignalSummary> summaries = new ArrayList<>();

        addSignalSummary(summaries, REAL_YIELD_CODE);
        addSignalSummary(summaries, DXY_CODE);

        return summaries;
    }

    private void addSignalSummary(List<WeeklySummaryDto.IndicatorSignalSummary> summaries, String indicatorCode) {
        indicatorService.findByCode(indicatorCode)
                .flatMap(signalEngineService::findLatestByIndicator)
                .ifPresent(signal -> summaries.add(toSignalSummary(signal)));
    }

    private WeeklySummaryDto.IndicatorSignalSummary toSignalSummary(Signal signal) {
        return WeeklySummaryDto.IndicatorSignalSummary.builder()
                .code(signal.getIndicator().getCode())
                .signal(signal.getSignalType().name())
                .confidence(signal.getConfidence())
                .build();
    }
}
