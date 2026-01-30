package com.goldlens.controller;

import com.goldlens.domain.GoldRiskSnapshot;
import com.goldlens.dto.GoldRiskDto;
import com.goldlens.service.GoldRiskAggregationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gold-risk")
public class GoldRiskController {

    private final GoldRiskAggregationService goldRiskAggregationService;

    public GoldRiskController(GoldRiskAggregationService goldRiskAggregationService) {
        this.goldRiskAggregationService = goldRiskAggregationService;
    }

    @GetMapping("/latest")
    public ResponseEntity<GoldRiskDto> getLatestGoldRisk() {
        return goldRiskAggregationService.findLatest()
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private GoldRiskDto toDto(GoldRiskSnapshot snapshot) {
        return GoldRiskDto.builder()
                .riskLevel(snapshot.getRiskLevel())
                .reason(snapshot.getReason())
                .asOfDate(snapshot.getAsOfDate())
                .build();
    }
}
