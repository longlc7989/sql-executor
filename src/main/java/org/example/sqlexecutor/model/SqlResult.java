package org.example.sqlexecutor.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SqlResult {
    private boolean success;
    private String message;
    private List<ColumnInfo> columns;
    private List<Map<String, Object>> data;
    private int affectedRows;
    private String queryType;
    private String dataSourceName;
    private long executionTime;

    // Thông tin phân trang
    private int currentPage;
    private int pageSize;
    private long totalItems;
    private int totalPages;

    public SqlResult() {
    }

}