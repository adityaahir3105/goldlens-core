package com.goldlens.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoldPricezResponse {

    @JsonProperty("ounce_price_usd")
    private String ouncePriceUsd;

    @JsonProperty("gmt_ounce_price_usd_updated")
    private String updatedAt;

    @JsonProperty("ounce_price_ask")
    private String ask;

    @JsonProperty("ounce_price_bid")
    private String bid;
}
