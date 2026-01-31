package com.goldlens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoldPriceHistoryResponse {

    private boolean historySupported;
    private String message;
    private String source;
    private List<GoldPriceHistoryItem> data;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GoldPriceHistoryItem {
        private String date;
        private String price;
        private String currency;
    }
}
