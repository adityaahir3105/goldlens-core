package com.goldlens.controller;

import com.goldlens.dto.GoldPriceSnapshot;
import com.goldlens.service.GoldPriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gold-price")
public class GoldPriceController {

    private final GoldPriceService goldPriceService;

    public GoldPriceController(GoldPriceService goldPriceService) {
        this.goldPriceService = goldPriceService;
    }

    @GetMapping("/latest")
    public ResponseEntity<GoldPriceSnapshot> getLatestPrice() {
        return goldPriceService.getLatestPrice()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
