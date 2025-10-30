package com.matillion.techtest2025.model;

import java.io.Serializable;

/**
 * Model class representing statistical information about a single column in a CSV dataset.
 * <p>
 * This record is part of the {@link com.matillion.techtest2025.controller.response.DataAnalysisResponse}
 * and provides per-column analysis results. Like {@code DataAnalysisResponse}, this is a Java record
 * that provides immutability and automatic generation of constructors, getters, equals, hashCode, and toString.
 * <p>
 * <b>Example usage in JSON response:</b>
 * <pre>
 * {
 *   "columnName": "age",
 *   "nullCount": 5,
 *   "uniqueCount": 42
 * }
 * </pre>
 *
 * @param columnName  the name of the column (from the CSV header)
 * @param nullCount   the number of null/empty values in this column
 * @param uniqueCount the number of unique non-null values in this column (Part 2 requirement)
 * @param dataType inferred data type of the column's non-null values (STRING, INTEGER, DECIMAL, BOOLEAN)
 */
public record ColumnStatistics(
        String columnName,
        int nullCount,
        int uniqueCount,
        String dataType,
        // ===== Part 3: Advanced statistics =====
        Double min,
        Double max,
        Double mean,
        Double median
) implements Serializable {
}
