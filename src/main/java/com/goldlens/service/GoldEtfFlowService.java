package com.goldlens.service;

import com.goldlens.domain.GoldEtfFlow;
import com.goldlens.dto.EtfFlowPointDto;
import com.goldlens.dto.EtfFlowResponseDto;
import com.goldlens.repository.GoldEtfFlowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoldEtfFlowService {

    private final GoldEtfFlowRepository repository;

    @Transactional(readOnly = true)
    public EtfFlowResponseDto getEtfFlows(int months) {
        LocalDate startDate = LocalDate.now().minusMonths(months).withDayOfMonth(1);

        List<GoldEtfFlow> flows = repository.findByDateAfterOrderByDateAsc(startDate);

        List<EtfFlowPointDto> points = flows.stream()
                .map(this::toDto)
                .toList();

        return EtfFlowResponseDto.builder()
                .source("World Gold Council")
                .points(points)
                .build();
    }

    @Transactional(readOnly = true)
    public EtfFlowResponseDto getAllEtfFlows() {
        List<GoldEtfFlow> flows = repository.findAllOrderByDateAsc();

        List<EtfFlowPointDto> points = flows.stream()
                .map(this::toDto)
                .toList();

        return EtfFlowResponseDto.builder()
                .source("World Gold Council")
                .points(points)
                .build();
    }

    @Transactional
    public int upsertFlow(GoldEtfFlow flow) {
        if (repository.existsByDateAndRegion(flow.getDate(), flow.getRegion())) {
            log.trace("Skipping duplicate: date={}, region={}", flow.getDate(), flow.getRegion());
            return 0;
        }
        repository.save(flow);
        return 1;
    }

    public long count() {
        return repository.count();
    }

    private EtfFlowPointDto toDto(GoldEtfFlow flow) {
        return EtfFlowPointDto.builder()
                .date(flow.getDate())
                .region(flow.getRegion())
                .holdingsTonnes(flow.getHoldingsTonnes())
                .netFlowTonnes(flow.getNetFlowTonnes())
                .build();
    }
}
