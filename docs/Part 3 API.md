# Part 3: Statistical Analysis Endpoint 🚀

## Overview
In Part 3, the service was extended to go beyond basic row/column/null/unique counts.  
The new **Statistical Analysis Endpoint** provides advanced numeric insights into CSV data.  

This makes the API more powerful and closer to real-world data profiling/analytics tools.  

---

## Problem
Before Part 3, the API could only tell you:
- How many rows and columns exist  
- Null counts per column  
- Unique counts per column  
- Inferred column type (STRING, INTEGER, DECIMAL, BOOLEAN)  

While useful, this lacks deeper **numeric statistics** that are often essential when exploring data for:
- Machine learning preprocessing  
- Data quality checks  
- Business analytics  

---

## Solution
A new endpoint was introduced:  

### `GET /api/analysis/{id}/statistics`  

This endpoint re-analyzes the original CSV data stored in the database and returns **advanced statistics for numeric columns**:

- `min` → smallest value  
- `max` → largest value  
- `mean` → average value  
- `median` → middle value  

For non-numeric columns, these fields return `null`.  

---

## API Endpoints (Part 3 Additions)

### 1. Ingest CSV (from Part 1 & 2)
```http
POST /api/analysis/ingestCsv
Content-Type: text/csv
```

Uploads and analyzes a new CSV file.  

---

### 2. Get Analysis by ID (from Part 2)
```http
GET /api/analysis/{id}
```

Returns basic analysis (no advanced stats).  

---

### 3. Delete Analysis by ID (from Part 2)
```http
DELETE /api/analysis/{id}
```

Deletes an analysis and all associated column statistics.  

---

### 4. 🚀 New: Get Advanced Statistics (Part 3)
```http
GET /api/analysis/{id}/statistics
```

Returns the same analysis but with **min, max, mean, and median** for numeric columns.  

---

## Example

### CSV Input
```csv
driver,number,team
Max Verstappen,1,Red Bull Racing
Lewis Hamilton,44,Mercedes
Charles Leclerc,16,Ferrari
```

### Request
```http
GET /api/analysis/7/statistics
```

### Response
```json
{
  "id": 7,
  "numberOfRows": 3,
  "numberOfColumns": 3,
  "totalCharacters": 97,
  "columnStatistics": [
    {
      "columnName": "driver",
      "nullCount": 0,
      "uniqueCount": 3,
      "dataType": "STRING",
      "min": null,
      "max": null,
      "mean": null,
      "median": null
    },
    {
      "columnName": "number",
      "nullCount": 0,
      "uniqueCount": 3,
      "dataType": "INTEGER",
      "min": 1,
      "max": 44,
      "mean": 20.33,
      "median": 16
    },
    {
      "columnName": "team",
      "nullCount": 0,
      "uniqueCount": 3,
      "dataType": "STRING",
      "min": null,
      "max": null,
      "mean": null,
      "median": null
    }
  ],
  "createdAt": "2025-10-29T23:56:19Z"
}
```

---

## Benefits
- Makes the service **data-science friendly**  
- Gives analysts deeper insights into numeric data without exporting to another tool  
- Extensible design (future: standard deviation, variance, correlations, etc.)  

---

## How to Test in Swagger
1. Run the app:  
   ```bash
   ./gradlew bootRun
   ```
2. Open Swagger UI:  
   👉 [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)  
3. **POST /api/analysis/ingestCsv** → upload a CSV with numeric columns.  
4. Copy the returned `id`.  
5. **GET /api/analysis/{id}/statistics** → paste the `id` and click *Execute*.  
6. You’ll see advanced stats (`min`, `max`, `mean`, `median`) for numeric columns.  

---

## Example Use Cases
- Quickly profile datasets before feeding them into ML models  
- Detect outliers (via min/max)  
- Spot missing or skewed numeric values  
- Combine with existing unique/null counts for a full data quality report  

---

✅ With Part 3, this project now covers:  
- **Data ingestion** (Part 1)  
- **Persistence & retrieval** (Part 2)  
- **Advanced statistics & insights** (Part 3)  
