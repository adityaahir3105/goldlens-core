package com.goldlens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicatorDto {

    private String code;
    private String name;
    private String unit;
    private boolean active;
}
