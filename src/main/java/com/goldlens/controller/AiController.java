package com.goldlens.controller;

import com.goldlens.ai.ExplainService;
import com.goldlens.domain.GoldRiskSnapshot;
import com.goldlens.domain.Indicator;
import com.goldlens.domain.IndicatorValue;
import com.goldlens.domain.Signal;
import com.goldlens.dto.ExplainGoldRiskResponse;
import com.goldlens.dto.ExplainIndicatorRequest;
import com.goldlens.dto.ExplainIndicatorResponse;
import com.goldlens.dto.ExplainSignalRequest;
import com.goldlens.dto.ExplainSignalResponse;
import com.goldlens.service.GoldRiskAggregationService;
import com.goldlens.service.IndicatorService;
import com.goldlens.service.IndicatorValueService;
import com.goldlens.service.SignalEngineService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final String REAL_YIELD_CODE = "US_10Y_REAL_YIELD";
    private static final String DXY_CODE = "US_DOLLAR_INDEX";

    private final IndicatorService indicatorService;
    private final IndicatorValueService indicatorValueService;
    private final SignalEngineService signalEngineService;
    private final GoldRiskAggregationService goldRiskAggregationService;
    private final ExplainService explainService;

    public AiController(IndicatorService indicatorService,
                        IndicatorValueService indicatorValueService,
                        SignalEngineService signalEngineService,
                        GoldRiskAggregationService goldRiskAggregationService,
                        ExplainService explainService) {
        this.indicatorService = indicatorService;
        this.indicatorValueService = indicatorValueService;
        this.signalEngineService = signalEngineService;
        this.goldRiskAggregationService = goldRiskAggregationService;
        this.explainService = explainService;
    }

    @PostMapping("/explain/indicator")
    public ResponseEntity<ExplainIndicatorResponse> explainIndicator(
            @Valid @RequestBody ExplainIndicatorRequest request) {

        Optional<Indicator> indicatorOpt = indicatorService.findByCode(request.getIndicatorCode());
        if (indicatorOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Indicator indicator = indicatorOpt.get();
        Optional<IndicatorValue> latestValueOpt = indicatorValueService.findLatestByIndicator(indicator);
        if (latestValueOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String explanation = explainService.explainIndicator(indicator, latestValueOpt.get());

        ExplainIndicatorResponse response = ExplainIndicatorResponse.builder()
                .indicatorCode(indicator.getCode())
                .explanation(explanation)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/explain/signal")
    public ResponseEntity<ExplainSignalResponse> explainSignal(
            @Valid @RequestBody ExplainSignalRequest request) {

        Optional<Indicator> indicatorOpt = indicatorService.findByCode(request.getIndicatorCode());
        if (indicatorOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Indicator indicator = indicatorOpt.get();
        Optional<Signal> signalOpt = signalEngineService.findLatestByIndicator(indicator);
        if (signalOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Signal signal = signalOpt.get();
        String explanation = explainService.explainSignal(signal);

        ExplainSignalResponse response = ExplainSignalResponse.builder()
                .indicatorCode(indicator.getCode())
                .signalType(signal.getSignalType())
                .explanation(explanation)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/explain/gold-risk")
    public ResponseEntity<ExplainGoldRiskResponse> explainGoldRisk() {
        Optional<GoldRiskSnapshot> snapshotOpt = goldRiskAggregationService.findLatest();
        if (snapshotOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        GoldRiskSnapshot snapshot = snapshotOpt.get();
        List<Signal> signals = collectLatestSignals();

        String explanation = explainService.explainGoldRisk(snapshot, signals);

        ExplainGoldRiskResponse response = ExplainGoldRiskResponse.builder()
                .riskLevel(snapshot.getRiskLevel())
                .asOfDate(snapshot.getAsOfDate())
                .explanation(explanation)
                .build();

        return ResponseEntity.ok(response);
    }

    private List<Signal> collectLatestSignals() {
        List<Signal> signals = new ArrayList<>();

        indicatorService.findByCode(REAL_YIELD_CODE)
                .flatMap(signalEngineService::findLatestByIndicator)
                .ifPresent(signals::add);

        indicatorService.findByCode(DXY_CODE)
                .flatMap(signalEngineService::findLatestByIndicator)
                .ifPresent(signals::add);

        return signals;
    }
}
