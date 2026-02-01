package com.goldlens.service;

import com.goldlens.domain.GoldEtfFlow;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@Slf4j
public class GoldEtfExcelImporter {

    private static final String EXCEL_FILE = "ETF_Flows_December_2025.xlsx";
    private static final String SHEET_NAME = "Charts Data";

    private static final int FLOW_DATE_COL = 9;
    private static final int FLOW_NA_COL = 10;
    private static final int FLOW_EU_COL = 11;
    private static final int FLOW_ASIA_COL = 12;
    private static final int FLOW_OTHER_COL = 13;

    private static final int HOLDINGS_DATE_COL = 34;
    private static final int HOLDINGS_NA_COL = 35;
    private static final int HOLDINGS_EU_COL = 36;
    private static final int HOLDINGS_ASIA_COL = 37;
    private static final int HOLDINGS_OTHER_COL = 38;

    private static final int DATA_START_ROW = 2;

    private static final String[] REGIONS = {"North America", "Europe", "Asia", "Other"};

    public List<GoldEtfFlow> parseExcel() {
        Map<String, GoldEtfFlow> flowMap = new LinkedHashMap<>();

        try (InputStream is = new ClassPathResource(EXCEL_FILE).getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                log.error("Sheet '{}' not found in Excel file", SHEET_NAME);
                return new ArrayList<>();
            }

            log.info("Starting Excel parsing from sheet '{}'", SHEET_NAME);

            int rowCount = sheet.getLastRowNum();
            log.info("Total rows in sheet: {}", rowCount);

            for (int rowIdx = DATA_START_ROW; rowIdx <= rowCount; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                parseFlowsSection(row, flowMap);
                parseHoldingsSection(row, flowMap);
            }

            log.info("Parsed {} unique ETF flow records from Excel", flowMap.size());

        } catch (Exception e) {
            log.error("Error parsing Excel file: {}", e.getMessage(), e);
        }

        return new ArrayList<>(flowMap.values());
    }

    private void parseFlowsSection(Row row, Map<String, GoldEtfFlow> flowMap) {
        LocalDate flowDate = parseDateCell(row.getCell(FLOW_DATE_COL));
        if (flowDate == null) return;

        LocalDate normalizedDate = LocalDate.of(flowDate.getYear(), flowDate.getMonth(), 1);

        int[] flowCols = {FLOW_NA_COL, FLOW_EU_COL, FLOW_ASIA_COL, FLOW_OTHER_COL};

        for (int i = 0; i < REGIONS.length; i++) {
            BigDecimal flowValue = parseNumericCell(row.getCell(flowCols[i]));
            if (flowValue == null) continue;

            String key = normalizedDate + "_" + REGIONS[i];
            GoldEtfFlow existing = flowMap.get(key);

            if (existing != null) {
                existing.setNetFlowTonnes(flowValue);
            } else {
                GoldEtfFlow etfFlow = GoldEtfFlow.builder()
                        .date(normalizedDate)
                        .region(REGIONS[i])
                        .netFlowTonnes(flowValue)
                        .build();
                flowMap.put(key, etfFlow);
            }
        }
    }

    private void parseHoldingsSection(Row row, Map<String, GoldEtfFlow> flowMap) {
        LocalDate holdingsDate = parseDateCell(row.getCell(HOLDINGS_DATE_COL));
        if (holdingsDate == null) return;

        LocalDate normalizedDate = LocalDate.of(holdingsDate.getYear(), holdingsDate.getMonth(), 1);

        int[] holdingsCols = {HOLDINGS_NA_COL, HOLDINGS_EU_COL, HOLDINGS_ASIA_COL, HOLDINGS_OTHER_COL};

        for (int i = 0; i < REGIONS.length; i++) {
            BigDecimal holdingsValue = parseNumericCell(row.getCell(holdingsCols[i]));
            if (holdingsValue == null) continue;

            String key = normalizedDate + "_" + REGIONS[i];
            GoldEtfFlow existing = flowMap.get(key);

            if (existing != null) {
                existing.setHoldingsTonnes(holdingsValue);
            } else {
                GoldEtfFlow etfFlow = GoldEtfFlow.builder()
                        .date(normalizedDate)
                        .region(REGIONS[i])
                        .holdingsTonnes(holdingsValue)
                        .build();
                flowMap.put(key, etfFlow);
            }
        }
    }

    private LocalDate parseDateCell(Cell cell) {
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date date = cell.getDateCellValue();
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (Exception e) {
            log.trace("Could not parse date from cell: {}", e.getMessage());
        }
        return null;
    }

    private BigDecimal parseNumericCell(Cell cell) {
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                double value = cell.getNumericCellValue();
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    return null;
                }
                return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
            } else if (cell.getCellType() == CellType.STRING) {
                String strValue = cell.getStringCellValue().trim();
                if (strValue.isEmpty() || strValue.equals("-") || strValue.equalsIgnoreCase("n/a")) {
                    return null;
                }
                strValue = strValue.replace(",", "");
                return new BigDecimal(strValue).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.trace("Could not parse numeric value from cell: {}", e.getMessage());
        }
        return null;
    }
}
