# Part 3: New API Feature - Statistical Analysis Endpoint 

## Overview
In Part 3, the service was extended to go beyond basic row/column/null/unique counts.  
The new **Statistical Analysis Endpoint** provides advanced numeric insights into CSV data.  

This makes the API more powerful and closer to real-world data profiling/analytics tools.  

---

## Problem it solves
Before Part 3, the API could only show:
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
GET /api/analysis/1/statistics
```

### Response
```json
{
  "id": 1,
  "numberOfRows": 3,
  "numberOfColumns": 3,
  "totalCharacters": 105,
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
      "mean": 20.333333333333332,
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
  "createdAt": "2025-10-31T20:44:40.3181869Z"
}
```

---
## Solution Success Image
![Test 2 Summary – 100% Successful](/images/newFeature.png)
![Test 2 Summary – 100% Successful](/images/newFeature2.png)

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

