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
                    uniqueValues.get(header[c]).add(val.trim());
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

        // Map entities to model for response
        List<ColumnStatistics> columnStatsModels = columnStatisticsEntities.stream()
                .map(e -> new ColumnStatistics(
                        e.getColumnName(),
                        e.getNullCount(),
                        e.getUniqueCount(),
                        e.getDataType()
                ))
                .collect(Collectors.toList());

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
                        stat.getDataType()
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
     */
    public void deleteAnalysisById(Long id) {
        DataAnalysisEntity entity = dataAnalysisRepository.findById(id).orElse(null);
        if (entity == null) {
            throw new NotFoundException("Analysis with id " + id + " not found");
        }
        dataAnalysisRepository.delete(entity);
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
        for (String v : values) {
            if (!(v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false"))) {
                return false;
            }
        }
        return true;
    }

    private boolean allInteger(Set<String> values) {
        for (String v : values) {
            if (!isInteger(v)) {
                return false;
            }
        }
        return true;
    }

    private boolean allDecimal(Set<String> values) {
        for (String v : values) {
            if (!isDecimal(v)) {
                return false;
            }
        }
        return true;
    }

    private boolean isInteger(String v) {
        try {
            // Accept optional leading +/-
            if (v == null) return false;
            String s = v.trim();
            if (s.isEmpty()) return false;
            // Disallow decimals like "1.0"
            if (s.contains(".") || s.contains("e") || s.contains("E")) return false;
            Long.parseLong(s);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isDecimal(String v) {
        try {
            if (v == null) return false;
            String s = v.trim();
            if (s.isEmpty()) return false;
            new BigDecimal(s);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
