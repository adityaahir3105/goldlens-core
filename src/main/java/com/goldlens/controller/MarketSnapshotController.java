package com.goldlens.controller;

import com.goldlens.dto.MarketSnapshotDto;
import com.goldlens.service.MarketSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketSnapshotController {

    private final MarketSnapshotService marketSnapshotService;

    public MarketSnapshotController(MarketSnapshotService marketSnapshotService) {
        this.marketSnapshotService = marketSnapshotService;
    }

    @GetMapping("/snapshot")
    public MarketSnapshotDto getMarketSnapshot(@RequestParam(defaultValue = "30") int days) {
        int cappedDays = Math.min(Math.max(days, 1), 365);
        return marketSnapshotService.getMarketSnapshot(cappedDays);
    }
}
