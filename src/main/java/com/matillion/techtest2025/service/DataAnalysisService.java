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
     * null counts and unique counts per column), infers data type for each column,
     * persists the results to the database, and returns the analysis.
     * enhanced for Part 3: also collects numeric values for later use
     * * when calculating advanced statistics.
     */
    public DataAnalysisResponse analyzeCsvData(String data) {
        if (data == null || data.trim().isEmpty()) {
            throw new BadRequestException("CSV data must not be empty");
        }

        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(data.trim()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1); // include empty trailing columns
                rows.add(parts);
            }
        } catch (Exception e) {
            throw new BadRequestException("Invalid CSV format");
        }

        if (rows.isEmpty() || rows.get(0).length == 0) {
            throw new BadRequestException("Invalid CSV header");
        }

        String[] header = rows.get(0);
        int numberOfColumns = header.length;

        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).length != numberOfColumns) {
                throw new BadRequestException(
                        String.format("Row %d has a different number of columns than the header", i + 1)
                );
            }
        }

        int numberOfRows = Math.max(0, rows.size() - 1);
        long totalCharacters = data.length();

        // Initialize maps for null counts, unique values (non-null), and container to collect non-null values for type inference
        Map<String, Integer> nullCounts = new LinkedHashMap<>();
        Map<String, Set<String>> uniqueValues = new LinkedHashMap<>();
        for (String column : header) {
            nullCounts.put(column, 0);
            uniqueValues.put(column, new HashSet<>());
        }

        // Iterate over data rows
        for (int i = 1; i < rows.size(); i++) {
            String[] values = rows.get(i);
            for (int c = 0; c < numberOfColumns; c++) {
                String val = values[c];
                if (val == null || val.isBlank()) {
                    nullCounts.put(header[c], nullCounts.get(header[c]) + 1);
                } else {
                    String trimmed = val.trim();
                    uniqueValues.get(header[c]).add(trimmed);
                    // ===== Part 3: keep track of numeric values =====
                    if (isDecimal(trimmed)) {
                        numericValues.get(header[c]).add(Double.valueOf(trimmed));
                    }
                }
            }
        }

        // Persist parent analysis entity
        OffsetDateTime creationTimestamp = OffsetDateTime.now();
        DataAnalysisEntity dataAnalysisEntity = DataAnalysisEntity.builder()
                .originalData(data)
                .numberOfRows(numberOfRows)
                .numberOfColumns(numberOfColumns)
                .totalCharacters(totalCharacters)
                .createdAt(creationTimestamp)
                .build();

        dataAnalysisRepository.save(dataAnalysisEntity);

        // Build and persist per-column statistics (with unique counts and inferred data type)
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

        // ===== Part 3: map ColumnStatistics with advanced stats =====
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

    /**
     * Retrieves a previously saved analysis by ID.
     *
     * @param id the ID of the analysis
     * @return the analysis response
     * @throws NotFoundException if no analysis exists with the given ID
     */
    public DataAnalysisResponse getAnalysisById(Long id) {
        DataAnalysisEntity entity = dataAnalysisRepository.findById(id).orElse(null);
        if (entity == null) {
            throw new NotFoundException("Analysis with id " + id + " not found");
        }

        List<ColumnStatistics> columnStatsModels = entity.getColumnStatistics().stream()
                .map(stat -> new ColumnStatistics(
                        stat.getColumnName(),
                        stat.getNullCount(),
                        stat.getUniqueCount(),
                        stat.getDataType(),
                        null, null, null, null // Part 3: advanced stats not recalculated here
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

    /**
     * Deletes an analysis by ID (cascade deletes column statistics).
     *
     * @param id the ID of the analysis
     * @throws NotFoundException if no analysis exists with the given ID
     *  * ===== Part 3 =====
     *      * New method: Re-analyze CSV to provide advanced stats (min, max, mean, median).
     */
    public DataAnalysisResponse getAnalysisStatistics(Long id) {
        DataAnalysisEntity entity = dataAnalysisRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Analysis with id " + id + " not found"));

        return analyzeCsvData(entity.getOriginalData());
    }

    public void deleteAnalysisById(Long id) {
        if (!dataAnalysisRepository.existsById(id)) {
            throw new NotFoundException("Analysis with id " + id + " not found");
        }
        dataAnalysisRepository.deleteById(id);
    }
    // -------------------- Helpers --------------------

    /**
     * Infers the data type of a column from its non-null values.
     * Priority:
     *  - BOOLEAN if all values are "true"/"false" (case-insensitive)
     *  - INTEGER if all values are integers
     *  - DECIMAL if all values are numeric (BigDecimal), but not all integers
     *  - STRING otherwise, or if there are no non-null values
     */
    private String inferDataType(Set<String> nonNullValues) {
        if (nonNullValues == null || nonNullValues.isEmpty()) {
            return "STRING";
        }

        if (allBoolean(nonNullValues)) {
            return "BOOLEAN";
        }

        if (allInteger(nonNullValues)) {
            return "INTEGER";
        }

        if (allDecimal(nonNullValues)) {
            return "DECIMAL";
        }

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

    private Double mean(List<Double> nums) {
        return nums.stream().mapToDouble(d -> d).average().orElse(Double.NaN);
    }

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
