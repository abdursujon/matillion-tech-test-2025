/* Annotated with test mappings for Part1, Part2 and Part3 (advanced statistics) */

package com.matillion.techtest2025.service;

import com.matillion.techtest2025.controller.response.DataAnalysisResponse;
import com.matillion.techtest2025.exception.BadRequestException;
import com.matillion.techtest2025.exception.NotFoundException;
import com.matillion.techtest2025.model.ColumnStatistics;
import com.matillion.techtest2025.repository.ColumnStatisticsRepository;
import com.matillion.techtest2025.repository.DataAnalysisRepository;
import com.matillion.techtest2025.repository.entity.ColumnStatisticsEntity;
import com.matillion.techtest2025.repository.entity.DataAnalysisEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service layer containing business logic for data analysis.
 * <p>
 * Responsible for parsing data, calculating statistics, inferring column data types,
 * persisting results, and retrieving/deleting analyses.
 *  Part 3: extended with advanced statistics (min, max, mean, median)
 *  and a new method getAnalysisStatistics(id).
 */
@Service
@RequiredArgsConstructor
public class DataAnalysisService {

    private final DataAnalysisRepository dataAnalysisRepository;
    private final ColumnStatisticsRepository columnStatisticsRepository;

    /**
     * Analyzes CSV data and returns statistics.
     * <p>
     * Parses the CSV, calculates statistics (row count, column count, character count,
     * null counts per column), persists the results to the database, and returns the analysis.
     * <p>
     * Part 3 extends this method to compute numeric statistics (min, max, mean, median)
     * for all numeric columns.
     */
    public DataAnalysisResponse analyzeCsvData(String data) {

        /*
         * Logic to handle failing test :
         * Part1Tests.shouldReturnBadRequestForInvalidCsv
         */
        if (data == null || data.trim().isEmpty()) {
            throw new BadRequestException("CSV data must not be empty");
        }

        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(data.trim()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                rows.add(parts);
            }
        } catch (Exception e) {
            throw new BadRequestException("Invalid CSV format");
        }

        /*
         * Logic to handle failing test :
         * Part1Tests.shouldHandleEmptyCsvWithHeaderOnly
         * Part1Tests.shouldAnalyzeSimpleCsv
         */
        if (rows.isEmpty() || rows.get(0).length == 0) {
            throw new BadRequestException("Invalid CSV header");
        }

        String[] header = rows.get(0);
        int numberOfColumns = header.length;

        /*
         * Logic to handle failing test :
         * Part1Tests.shouldReturnBadRequestForInvalidCsv
         */
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).length != numberOfColumns) {
                throw new BadRequestException(
                        String.format("Row %d has a different number of columns than the header", i + 1)
                );
            }
        }

        /*
         * Logic to handle failing test :
         * Part1Tests.shouldHandleSingleRowCsv
         * Part1Tests.shouldHandleLargeCsv
         */
        int numberOfRows = Math.max(0, rows.size() - 1);
        long totalCharacters = data.length();

        Map<String, Integer> nullCounts = new LinkedHashMap<>();
        Map<String, Set<String>> uniqueValues = new LinkedHashMap<>();

        /*
         * Part 3 addition :
         * Initialize numericValues map to store numeric values for each column.
         * These will later be used to compute min, max, mean, and median.
         */
        Map<String, List<Double>> numericValues = new LinkedHashMap<>();

        for (String column : header) {
            nullCounts.put(column, 0);
            uniqueValues.put(column, new HashSet<>());
            numericValues.put(column, new ArrayList<>());
        }

        // ===== Process rows =====
        for (int i = 1; i < rows.size(); i++) {
            String[] values = rows.get(i);
            for (int c = 0; c < numberOfColumns; c++) {
                String val = values[c];

                /*
                 * Logic to handle failing test :
                 * Part1Tests.shouldCountNullValuesCorrectly
                 * Part1Tests.shouldHandleMixedNullValues
                 * Part2Tests.shouldExcludeNullsFromUniqueCount
                 */
                if (val == null || val.isBlank()) {
                    nullCounts.put(header[c], nullCounts.get(header[c]) + 1);
                } else {
                    String trimmed = val.trim();

                    /*
                     * Logic to handle failing test :
                     * Part2Tests.shouldCalculateUniqueCountsForSimpleCsv
                     * Part2Tests.shouldCalculateUniqueCountsWithDuplicates
                     * Part2Tests.shouldReturnZeroUniqueCountForEmptyCsv
                     */
                    uniqueValues.get(header[c]).add(trimmed);

                    /*
                     * Part 3 addition :
                     * When a value is numeric (integer or decimal), store it for statistical analysis.
                     * These lists are used later to calculate min, max, mean, and median per column.
                     */
                    if (isDecimal(trimmed)) {
                        numericValues.get(header[c]).add(Double.valueOf(trimmed));
                    }
                }
            }
        }

        /*
         * Logic to handle failing test :
         * Part1Tests.shouldPersistCorrectAnalysisData
         * Part1Tests.shouldPersistColumnStatisticsEntities
         * Part2Tests.shouldPersistUniqueCountsToDatabase
         */
        OffsetDateTime creationTimestamp = OffsetDateTime.now();
        DataAnalysisEntity dataAnalysisEntity = DataAnalysisEntity.builder()
                .originalData(data)
                .numberOfRows(numberOfRows)
                .numberOfColumns(numberOfColumns)
                .totalCharacters(totalCharacters)
                .createdAt(creationTimestamp)
                .build();

        dataAnalysisRepository.save(dataAnalysisEntity);

        /*
         * Logic to handle failing test :
         * Part2Tests.shouldCalculateUniqueCountsForSimpleCsv
         * Part2Tests.shouldExcludeNullsFromUniqueCount
         */
        List<ColumnStatisticsEntity> columnStatisticsEntities = header.length == 0
                ? Collections.emptyList()
                : Arrays.stream(header)
                .map(col -> {
                    Set<String> nonNullValues = uniqueValues.get(col);
                    String inferredType = inferDataType(nonNullValues);
                    return ColumnStatisticsEntity.builder()
                            .dataAnalysis(dataAnalysisEntity)
                            .columnName(col)
                            .nullCount(nullCounts.get(col))
                            .uniqueCount(nonNullValues.size())
                            .dataType(inferredType)
                            .build();
                })
                .collect(Collectors.toList());

        columnStatisticsRepository.saveAll(columnStatisticsEntities);

        /*
         * Part 3 addition :
         * Build ColumnStatistics list that includes new fields:
         * min, max, mean, and median — for numeric columns only.
         */

        List<ColumnStatistics> columnStatsModels = Arrays.stream(header)
                .map(col -> {
                    Set<String> nonNull = uniqueValues.get(col);
                    String type = inferDataType(nonNull);
                    List<Double> nums = numericValues.get(col);
                    return new ColumnStatistics(
                            col,
                            nullCounts.get(col),
                            nonNull.size(),
                            type,
                            nums.isEmpty() ? null : Collections.min(nums),
                            nums.isEmpty() ? null : Collections.max(nums),
                            nums.isEmpty() ? null : mean(nums),
                            nums.isEmpty() ? null : median(nums)
                    );
                })
                .toList();

        return new DataAnalysisResponse(
                dataAnalysisEntity.getId(),
                numberOfRows,
                numberOfColumns,
                totalCharacters,
                columnStatsModels,
                creationTimestamp
        );
    }

    public DataAnalysisResponse getAnalysisById(Long id) {

        /*
         * Logic to handle failing test :
         * Part2Tests.shouldReturn404ForNonExistentAnalysis
         * Part2Tests.shouldRetrievePreviousAnalysisById
         */
        DataAnalysisEntity entity = dataAnalysisRepository.findById(id).orElse(null);
        if (entity == null) {
            throw new NotFoundException("Analysis with id " + id + " not found");
        }

        /*
         * Logic to handle failing test :
         * Part2Tests.shouldRetrieveMultipleAnalysesIndependently
         */
        List<ColumnStatistics> columnStatsModels = entity.getColumnStatistics().stream()
                .map(stat -> new ColumnStatistics(
                        stat.getColumnName(),
                        stat.getNullCount(),
                        stat.getUniqueCount(),
                        stat.getDataType(),
                        null, null, null, null // Part 3 stats not recomputed here
                ))
                .collect(Collectors.toList());

        return new DataAnalysisResponse(
                entity.getId(),
                entity.getNumberOfRows(),
                entity.getNumberOfColumns(),
                entity.getTotalCharacters(),
                columnStatsModels,
                entity.getCreatedAt()
        );
    }

    /*
     * Part 3 addition :
     * New endpoint logic that re-analyzes stored CSV data
     * and returns results including numeric statistics (min, max, mean, median).
     */
    public DataAnalysisResponse getAnalysisStatistics(Long id) {
        DataAnalysisEntity entity = dataAnalysisRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Analysis with id " + id + " not found"));
        return analyzeCsvData(entity.getOriginalData());
    }

    public void deleteAnalysisById(Long id) {

        /*
         * Logic to handle failing test :
         * Part2Tests.shouldReturn404WhenDeletingNonExistentAnalysis
         */
        if (!dataAnalysisRepository.existsById(id)) {
            throw new NotFoundException("Analysis with id " + id + " not found");
        }

        /*
         * Logic to handle failing test :
         * Part2Tests.shouldDeleteAnalysisById
         * Part2Tests.shouldCascadeDeleteColumnStatistics
         * Part2Tests.shouldDeleteOnlySpecifiedAnalysis
         */
        dataAnalysisRepository.deleteById(id);
    }

    // -------------------- Part 3: Advanced Statistics Helpers --------------------

    private String inferDataType(Set<String> nonNullValues) {
        if (nonNullValues == null || nonNullValues.isEmpty()) return "STRING";
        if (allBoolean(nonNullValues)) return "BOOLEAN";
        if (allInteger(nonNullValues)) return "INTEGER";
        if (allDecimal(nonNullValues)) return "DECIMAL";
        return "STRING";
    }

    private boolean allBoolean(Set<String> values) {
        return values.stream().allMatch(v -> v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false"));
    }

    private boolean allInteger(Set<String> values) {
        return values.stream().allMatch(this::isInteger);
    }

    private boolean allDecimal(Set<String> values) {
        return values.stream().allMatch(this::isDecimal);
    }

    private boolean isInteger(String v) {
        try {
            if (v == null || v.isBlank()) return false;
            if (v.contains(".") || v.contains("e") || v.contains("E")) return false;
            Long.parseLong(v.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDecimal(String v) {
        try {
            if (v == null || v.isBlank()) return false;
            new BigDecimal(v.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /*
     * Part 3 addition :
     * Computes the arithmetic mean (average) for numeric values.
     */
    private Double mean(List<Double> nums) {
        return nums.stream().mapToDouble(d -> d).average().orElse(Double.NaN);
    }

    /*
     * Part 3 addition :
     * Computes the median value for numeric lists.
     * If count is even, returns average of two middle values.
     */
    private Double median(List<Double> nums) {
        if (nums.isEmpty()) return null;
        List<Double> sorted = new ArrayList<>(nums);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        } else {
            return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        }
    }
}
