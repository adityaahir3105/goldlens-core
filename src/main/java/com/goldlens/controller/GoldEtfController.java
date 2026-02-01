package com.goldlens.controller;

import com.goldlens.dto.EtfFlowResponseDto;
import com.goldlens.service.GoldEtfFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gold/etf")
@RequiredArgsConstructor
public class GoldEtfController {

    private final GoldEtfFlowService etfFlowService;

    @GetMapping("/flows")
    public ResponseEntity<EtfFlowResponseDto> getEtfFlows(
            @RequestParam(value = "months", defaultValue = "12") int months) {

        if (months <= 0) {
            months = 12;
        }

        EtfFlowResponseDto response = etfFlowService.getEtfFlows(months);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/flows/all")
    public ResponseEntity<EtfFlowResponseDto> getAllEtfFlows() {
        EtfFlowResponseDto response = etfFlowService.getAllEtfFlows();
        return ResponseEntity.ok(response);
    }
}
