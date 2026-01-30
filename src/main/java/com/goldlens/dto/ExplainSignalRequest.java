package com.goldlens.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExplainSignalRequest {

    @NotBlank(message = "indicatorCode is required")
    private String indicatorCode;
}
