package com.goldlens.dto;

import com.goldlens.domain.SignalType;
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
public class ExplainSignalResponse {

    private String indicatorCode;
    private SignalType signalType;
    private String explanation;
}
