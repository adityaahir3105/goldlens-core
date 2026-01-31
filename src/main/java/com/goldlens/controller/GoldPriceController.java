package com.goldlens.controller;

import com.goldlens.dto.GoldPriceSnapshot;
import com.goldlens.exception.GoldApiUnavailableException;
import com.goldlens.service.GoldPriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/gold-price")
public class GoldPriceController {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceController.class);

    private final GoldPriceService goldPriceService;

    public GoldPriceController(GoldPriceService goldPriceService) {
        this.goldPriceService = goldPriceService;
    }

    /**
     * Returns the latest gold price with caching (5 min TTL).
     * Response includes: price, currency, unit, asOf, source, isLive, supportsHistory
     */
    @GetMapping("/latest")
    public ResponseEntity<GoldPriceSnapshot> getLatestPrice() {
        GoldPriceSnapshot snapshot = goldPriceService.getLatestPriceWithFallback();
        return ResponseEntity.ok(snapshot);
    }

    @ExceptionHandler(GoldApiUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleGoldApiUnavailable(GoldApiUnavailableException ex) {
        HttpStatus status = ex.getRecommendedResponseStatus();

        log.error("[requestId={}] [errorType={}] GoldPricez unavailable, returning {}: {}",
                ex.getRequestId(), ex.getErrorType(), status.value(), ex.getMessage());

        Map<String, Object> errorBody = Map.of(
                "error", status.getReasonPhrase(),
                "message", ex.getMessage(),
                "errorType", ex.getErrorType(),
                "requestId", ex.getRequestId(),
                "providerStatus", ex.getHttpStatus(),
                "timestamp", Instant.now().toString(),
                "supportsHistory", false
        );

        return ResponseEntity.status(status).body(errorBody);
    }
}
