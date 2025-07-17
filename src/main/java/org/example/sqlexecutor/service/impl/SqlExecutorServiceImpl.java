package org.example.sqlexecutor.service.impl;

import org.example.sqlexecutor.config.SqlLogger;
import org.example.sqlexecutor.exception.SqlExecutionException;
import org.example.sqlexecutor.model.ColumnInfo;
import org.example.sqlexecutor.model.PageRequest;
import org.example.sqlexecutor.model.SqlQuery;
import org.example.sqlexecutor.model.SqlResult;
import org.example.sqlexecutor.service.DataSourceService;
import org.example.sqlexecutor.service.SqlExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class SqlExecutorServiceImpl implements SqlExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(SqlExecutorServiceImpl.class);

    @Autowired
    private DataSourceService dataSourceService;

    private static final int QUERY_TIMEOUT_SECONDS = 20;
    private static final Pattern COUNT_PATTERN = Pattern.compile("SELECT\\s+COUNT\\s*\\(", Pattern.CASE_INSENSITIVE);

    @Override
    @Transactional(timeout = QUERY_TIMEOUT_SECONDS)
    public SqlResult executeSql(SqlQuery sqlQuery) {
        SqlResult result = new SqlResult();
        String query = sqlQuery.getQuery().trim();
        PageRequest pagination = sqlQuery.getPagination();
        String dataSourceName = sqlQuery.getDataSourceName();

        // Lấy JdbcTemplate theo dataSourceName
        JdbcTemplate jdbcTemplate = dataSourceService.getJdbcTemplate(dataSourceName);

        // Log SQL query
        logger.info("Executing SQL on datasource [{}]: {}", dataSourceName, query);
        SqlLogger.logSqlQuery("DATASOURCE[" + dataSourceName + "]: " + query);

        long startTime = System.currentTimeMillis();

        try {
            // Thiết lập timeout cho JdbcTemplate
            jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            // Xác định loại truy vấn
            String queryType = determineQueryType(query);
            result.setQueryType(queryType);
            result.setDataSourceName(dataSourceName);

            if ("SELECT".equals(queryType)) {
                // Thiết lập thông tin phân trang
                result.setCurrentPage(pagination.getPage());
                result.setPageSize(pagination.getSize());

                // Đếm tổng số bản ghi với timeout và tối ưu
                long totalItems = countRecordsOptimized(jdbcTemplate, query);
                result.setTotalItems(totalItems);
                result.setTotalPages((int) Math.ceil((double) totalItems / pagination.getSize()));

                // Thực hiện truy vấn với phân trang
                executeSelectWithPagination(jdbcTemplate, query, result, pagination);
            } else {
                executeUpdate(jdbcTemplate, query, result);
            }

            result.setSuccess(true);
            result.setMessage("Query executed successfully");

            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTime(executionTime);
            logger.info("Query executed in {} ms", executionTime);

        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Error executing query: " + e.getMessage());
            logger.error("Error executing query", e);
            throw new SqlExecutionException("Error executing SQL query", e);
        }

        return result;
    }

    @Override
    public String determineQueryType(String query) {
        String upperQuery = query.toUpperCase();
        if (Pattern.compile("^\\s*SELECT\\s+").matcher(upperQuery).find()) {
            return "SELECT";
        } else if (Pattern.compile("^\\s*INSERT\\s+").matcher(upperQuery).find()) {
            return "INSERT";
        } else if (Pattern.compile("^\\s*UPDATE\\s+").matcher(upperQuery).find()) {
            return "UPDATE";
        } else if (Pattern.compile("^\\s*DELETE\\s+").matcher(upperQuery).find()) {
            return "DELETE";
        } else if (Pattern.compile("^\\s*CREATE\\s+").matcher(upperQuery).find()) {
            return "CREATE";
        } else if (Pattern.compile("^\\s*ALTER\\s+").matcher(upperQuery).find()) {
            return "ALTER";
        } else if (Pattern.compile("^\\s*DROP\\s+").matcher(upperQuery).find()) {
            return "DROP";
        } else {
            return "OTHER";
        }
    }

    @Override
    public long countRecords(JdbcTemplate jdbcTemplate, String query) {
        return countRecordsOptimized(jdbcTemplate, query);
    }

    private long countRecordsOptimized(JdbcTemplate jdbcTemplate, String query) {
        if (COUNT_PATTERN.matcher(query).find()) {
            try {
                return jdbcTemplate.queryForObject(query, Long.class);
            } catch (DataAccessException e) {
                throw new SqlExecutionException("Error counting records", e);
            }
        }

        String optimizedQuery = query.toLowerCase();
        int orderByIndex = optimizedQuery.lastIndexOf("order by");
        if (orderByIndex > 0) {
            query = query.substring(0, orderByIndex);
        }

        String countQuery = "SELECT COUNT(*) FROM (" + query + ") AS count_query";

        try {
            return jdbcTemplate.queryForObject(countQuery, Long.class);
        } catch (DataAccessException e) {
            countQuery = "SELECT COUNT(1) FROM (" + query + ") AS count_query";
            try {
                return jdbcTemplate.queryForObject(countQuery, Long.class);
            } catch (DataAccessException ex) {
                throw new SqlExecutionException("Error counting records", ex);
            }
        }
    }

    private void executeSelectWithPagination(JdbcTemplate jdbcTemplate, String query, SqlResult result, PageRequest pagination) {
        // Cập nhật để nhận JdbcTemplate làm tham số
        String paginatedQuery = getPaginatedQuery(jdbcTemplate, query, pagination);

        logger.info("Paginated query: {}", paginatedQuery);
        SqlLogger.logSqlQuery(paginatedQuery);

        SqlRowSet rowSet = jdbcTemplate.queryForRowSet(paginatedQuery);
        SqlRowSetMetaData metaData = rowSet.getMetaData();

        List<ColumnInfo> columns = new ArrayList<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String columnName = metaData.getColumnName(i);
            String columnType = metaData.getColumnTypeName(i);

            if (columnType.equals("VARCHAR") || columnType.equals("CHAR")) {
                int columnSize = metaData.getColumnDisplaySize(i);
                columnType = columnType + "(" + columnSize + ")";
            } else if (columnType.equals("DECIMAL") || columnType.equals("NUMERIC")) {
                int precision = metaData.getPrecision(i);
                int scale = metaData.getScale(i);
                columnType = columnType + "(" + precision + "," + scale + ")";
            }

            columns.add(new ColumnInfo(columnName, columnType));
        }
        result.setColumns(columns);

        List<Map<String, Object>> data = new ArrayList<>();
        while (rowSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (ColumnInfo column : columns) {
                row.put(column.getName(), rowSet.getObject(column.getName()));
            }
            data.add(row);
        }
        result.setData(data);
        result.setAffectedRows(data.size());
    }

    private void executeUpdate(JdbcTemplate jdbcTemplate, String query, SqlResult result) {
        int rows = jdbcTemplate.update(query);
        result.setAffectedRows(rows);
        result.setColumns(Collections.emptyList());
        result.setData(Collections.emptyList());

        result.setCurrentPage(0);
        result.setPageSize(0);
        result.setTotalItems(0);
        result.setTotalPages(0);
    }

    private String getPaginatedQuery(JdbcTemplate jdbcTemplate, String query, PageRequest pagination) {
        String dbProductName = "";
        try {
            dbProductName = jdbcTemplate.getDataSource().getConnection().getMetaData().getDatabaseProductName().toLowerCase();
        } catch (Exception e) {
            dbProductName = "mysql";
        }

        switch (dbProductName) {
            case "oracle":
                int offset = pagination.getOffset();
                int limit = pagination.getSize();
                return "SELECT * FROM (" +
                        "SELECT a.*, ROWNUM rnum FROM (" + query + ") a " +
                        "WHERE ROWNUM <= " + (offset + limit) + ") " +
                        "WHERE rnum > " + offset;

            case "microsoft sql server":
                return query + " OFFSET " + pagination.getOffset() +
                        " ROWS FETCH NEXT " + pagination.getSize() + " ROWS ONLY";

            case "db2":
                return query + " LIMIT " + pagination.getSize() +
                        " OFFSET " + pagination.getOffset();

            case "postgresql":
            case "mysql":
            case "mariadb":
            default:
                return query + " LIMIT " + pagination.getSize() +
                        " OFFSET " + pagination.getOffset();
        }
    }
}