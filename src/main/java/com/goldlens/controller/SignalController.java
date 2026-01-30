package com.goldlens.controller;

import com.goldlens.domain.Signal;
import com.goldlens.dto.SignalDto;
import com.goldlens.service.IndicatorService;
import com.goldlens.service.SignalEngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/signals")
public class SignalController {

    private final IndicatorService indicatorService;
    private final SignalEngineService signalEngineService;

    public SignalController(IndicatorService indicatorService, SignalEngineService signalEngineService) {
        this.indicatorService = indicatorService;
        this.signalEngineService = signalEngineService;
    }

    @GetMapping("/{indicatorCode}/latest")
    public ResponseEntity<SignalDto> getLatestSignal(@PathVariable String indicatorCode) {
        return indicatorService.findByCode(indicatorCode)
                .flatMap(signalEngineService::findLatestByIndicator)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private SignalDto toDto(Signal signal) {
        return SignalDto.builder()
                .indicatorCode(signal.getIndicator().getCode())
                .signalType(signal.getSignalType())
                .reason(signal.getReason())
                .asOfDate(signal.getAsOfDate())
                .confidence(signal.getConfidence())
                .build();
    }
}
