package com.matillion.techtest2025.controller;

import com.matillion.techtest2025.controller.response.DataAnalysisResponse;
import com.matillion.techtest2025.exception.BadRequestException;
import com.matillion.techtest2025.service.DataAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.NO_CONTENT;

/**
 * REST controller for data analysis endpoints.
 * <p>
 * Handles HTTP requests and delegates business logic to {@link DataAnalysisService}.
 * All endpoints are prefixed with {@code /api/analysis}.
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class DataAnalysisController {

    private final DataAnalysisService dataAnalysisService;

    // Part 1 endpoint

    /**
     * Ingests and analyzes CSV data.
     *
     * @param csvContent the raw CSV data as plain text
     * @return analysis results
     * @throws BadRequestException if validation fails or CSV is invalid
     */
    @PostMapping(
            value = "/ingestCsv",
            consumes = {"text/plain", "text/csv"},
            produces = "application/json"
    )
    public ResponseEntity<DataAnalysisResponse> ingestAndAnalyzeCsv(@RequestBody String csvContent) {
        if (csvContent.contains("Sonny Hayes")) {
            throw new BadRequestException("CSV data containing 'Sonny Hayes' is not allowed");
        }

        DataAnalysisResponse response = dataAnalysisService.analyzeCsvData(csvContent);
        return ResponseEntity.ok(response);
    }

    // Part 2 endpoints

    /**
     * Retrieves a previously analyzed CSV by its ID.
     *
     * @param id the ID of the analysis
     * @return analysis results
     */
    @GetMapping("/{id}")
    public DataAnalysisResponse getAnalysisById(@PathVariable Long id) {
        return dataAnalysisService.getAnalysisById(id);
    }

    /**
     * Deletes an analysis by its ID.
     *
     * @param id the ID of the analysis
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteAnalysisById(@PathVariable Long id) {
        dataAnalysisService.deleteAnalysisById(id);
    }
}
