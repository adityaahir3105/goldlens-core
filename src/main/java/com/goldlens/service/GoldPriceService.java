package com.goldlens.service;

import com.goldlens.client.GoldPricezClient;
import com.goldlens.dto.GoldPriceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GoldPriceService {

    private static final Logger log = LoggerFactory.getLogger(GoldPriceService.class);

    private final GoldPricezClient goldPricezClient;

    public GoldPriceService(GoldPricezClient goldPricezClient) {
        this.goldPricezClient = goldPricezClient;
    }

    /**
     * Returns the latest gold price by fetching directly from GoldPricez API.
     * Throws GoldApiUnavailableException on any failure.
     */
    public GoldPriceSnapshot getLatestPrice() {
        log.debug("Fetching latest gold price from GoldPricez");
        return goldPricezClient.fetchLatestGoldPrice();
    }
}
