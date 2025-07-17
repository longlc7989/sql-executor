package org.example.sqlexecutor.controller;

import org.example.sqlexecutor.adapter.SqlDatabaseAdapter;
import org.example.sqlexecutor.service.DataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/sql/suggestions")
@CrossOrigin(origins = "*")
public class SqlSuggestionController {
    private static final Logger logger = LoggerFactory.getLogger(SqlSuggestionController.class);

    @Autowired
    private DataSourceService dataSourceService;

    private static final List<String> COMMON_SQL_KEYWORDS = Arrays.asList(
            "SELECT", "FROM", "WHERE", "GROUP BY", "HAVING", "ORDER BY",
            "INSERT", "UPDATE", "DELETE", "JOIN", "LEFT JOIN", "RIGHT JOIN",
            "INNER JOIN", "FULL JOIN", "UNION", "CREATE", "ALTER", "DROP",
            "TABLE", "VIEW", "INDEX", "AND", "OR", "NOT", "IN", "BETWEEN",
            "LIKE", "IS NULL", "IS NOT NULL"
    );

    private static final List<String> COMMON_SQL_FUNCTIONS = Arrays.asList(
            "COUNT", "SUM", "AVG", "MIN", "MAX", "CONCAT", "SUBSTRING",
            "TRIM", "LENGTH", "UPPER", "LOWER", "ROUND", "NOW()"
    );

    // Database-specific keywords
    private static final Map<String, List<String>> DB_SPECIFIC_KEYWORDS = new HashMap<>();
    static {
        // MySQL keywords
        DB_SPECIFIC_KEYWORDS.put("mysql", Arrays.asList(
                "LIMIT", "OFFSET", "SHOW", "DESCRIBE", "EXPLAIN", "USE", "IGNORE"
        ));

        // SQL Server keywords
        DB_SPECIFIC_KEYWORDS.put("sqlserver", Arrays.asList(
                "TOP", "OFFSET FETCH", "OUTPUT", "IDENTITY", "MERGE", "PIVOT", "UNPIVOT",
                "OVER", "PARTITION BY", "WITH TIES", "CROSS APPLY", "OUTER APPLY"
        ));

        // Oracle keywords
        DB_SPECIFIC_KEYWORDS.put("oracle", Arrays.asList(
                "ROWNUM", "ROWID", "CONNECT BY", "START WITH", "PRIOR", "LEVEL",
                "CONNECT_BY_ROOT", "SYS_CONNECT_BY_PATH", "NOCYCLE"
        ));
    }

    // Database-specific functions
    private static final Map<String, List<String>> DB_SPECIFIC_FUNCTIONS = new HashMap<>();
    static {
        // MySQL functions
        DB_SPECIFIC_FUNCTIONS.put("mysql", Arrays.asList(
                "NOW()", "CURDATE()", "CURTIME()", "DATE_FORMAT", "STR_TO_DATE",
                "IFNULL", "IF", "CASE", "GROUP_CONCAT"
        ));

        // SQL Server functions
        DB_SPECIFIC_FUNCTIONS.put("sqlserver", Arrays.asList(
                "GETDATE()", "GETUTCDATE()", "ISNULL", "COALESCE", "CONVERT", "CAST",
                "DATEADD", "DATEDIFF", "DATENAME", "FORMAT", "STRING_AGG"
        ));

        // Oracle functions
        DB_SPECIFIC_FUNCTIONS.put("oracle", Arrays.asList(
                "SYSDATE", "SYSTIMESTAMP", "NVL", "DECODE", "TO_CHAR", "TO_DATE",
                "TO_NUMBER", "LISTAGG", "REGEXP_LIKE", "REGEXP_REPLACE"
        ));
    }

    @GetMapping("/all")
    public Map<String, Object> getAllSuggestions(
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "mysql") String dataSource) {

        Map<String, Object> result = new HashMap<>();
        String searchTerm = query != null ? query.toLowerCase() : "";

        // Lấy JdbcTemplate của data source đã chọn
        JdbcTemplate jdbcTemplate = dataSourceService.getJdbcTemplate(dataSource);
        String dbType = dataSourceService.getDatabaseType(dataSource);
        SqlDatabaseAdapter.DatabaseStrategy strategy = dataSourceService.getDatabaseStrategy(dataSource);

        result.put("tables", getFilteredTables(jdbcTemplate, strategy, searchTerm));
        result.put("keywords", getFilteredKeywords(dbType, searchTerm));
        result.put("functions", getFilteredFunctions(dbType, searchTerm));

        return result;
    }

    @GetMapping("/tables")
    public List<Map<String, Object>> getTables(
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "mysql") String dataSource) {

        String searchTerm = query != null ? query.toLowerCase() : "";
        JdbcTemplate jdbcTemplate = dataSourceService.getJdbcTemplate(dataSource);
        SqlDatabaseAdapter.DatabaseStrategy strategy = dataSourceService.getDatabaseStrategy(dataSource);

        return getFilteredTables(jdbcTemplate, strategy, searchTerm);
    }

    @GetMapping("/columns")
    public List<Map<String, Object>> getColumns(
            @RequestParam String table,
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "mysql") String dataSource) {

        String searchTerm = query != null ? query.toLowerCase() : "";
        JdbcTemplate jdbcTemplate = dataSourceService.getJdbcTemplate(dataSource);
        SqlDatabaseAdapter.DatabaseStrategy strategy = dataSourceService.getDatabaseStrategy(dataSource);

        return getFilteredColumns(jdbcTemplate, strategy, table, searchTerm);
    }

    @GetMapping("/keywords")
    public List<Map<String, Object>> getKeywords(
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "mysql") String dataSource) {

        String searchTerm = query != null ? query.toLowerCase() : "";
        String dbType = dataSourceService.getDatabaseType(dataSource);

        return getFilteredKeywords(dbType, searchTerm);
    }

    @GetMapping("/functions")
    public List<Map<String, Object>> getFunctions(
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "mysql") String dataSource) {

        String searchTerm = query != null ? query.toLowerCase() : "";
        String dbType = dataSourceService.getDatabaseType(dataSource);

        return getFilteredFunctions(dbType, searchTerm);
    }

    private List<Map<String, Object>> getFilteredTables(
            JdbcTemplate jdbcTemplate,
            SqlDatabaseAdapter.DatabaseStrategy strategy,
            String searchTerm) {

        List<Map<String, Object>> tables = new ArrayList<>();

        try {
            String query = strategy.getTableListQuery();
            jdbcTemplate.query(query, rs -> {
                String tableName = rs.getString(1);

                if (searchTerm.isEmpty() || tableName.toLowerCase().contains(searchTerm)) {
                    Map<String, Object> table = new HashMap<>();
                    table.put("name", tableName);
                    table.put("type", "table");
                    table.put("description", "Table");
                    tables.add(table);
                }
            });
        } catch (Exception e) {
            logger.error("Error getting tables", e);
        }

        return tables;
    }

    private List<Map<String, Object>> getFilteredColumns(
            JdbcTemplate jdbcTemplate,
            SqlDatabaseAdapter.DatabaseStrategy strategy,
            String table,
            String searchTerm) {

        List<Map<String, Object>> columns = new ArrayList<>();

        try {
            String query = strategy.getColumnListQuery(table);
            jdbcTemplate.query(query, rs -> {
                String columnName = rs.getString(1);
                String dataType = rs.getString(2);

                if (searchTerm.isEmpty() || columnName.toLowerCase().contains(searchTerm)) {
                    Map<String, Object> column = new HashMap<>();
                    column.put("name", columnName);
                    column.put("type", "column");
                    column.put("description", dataType);
                    column.put("table", table);
                    columns.add(column);
                }
            });
        } catch (Exception e) {
            logger.error("Error getting columns for table: " + table, e);
        }

        return columns;
    }

    private List<Map<String, Object>> getFilteredKeywords(String dbType, String searchTerm) {
        List<Map<String, Object>> keywords = new ArrayList<>();

        // Kết hợp từ khóa chung và từ khóa đặc thù cho loại DB
        List<String> allKeywords = new ArrayList<>(COMMON_SQL_KEYWORDS);
        List<String> dbSpecificKeywords = DB_SPECIFIC_KEYWORDS.getOrDefault(dbType, Collections.emptyList());
        allKeywords.addAll(dbSpecificKeywords);

        for (String keyword : allKeywords) {
            if (searchTerm.isEmpty() || keyword.toLowerCase().contains(searchTerm)) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", keyword);
                item.put("type", "keyword");
                item.put("description", "SQL Keyword");
                keywords.add(item);
            }
        }

        return keywords;
    }

    private List<Map<String, Object>> getFilteredFunctions(String dbType, String searchTerm) {
        List<Map<String, Object>> functions = new ArrayList<>();

        // Kết hợp hàm chung và hàm đặc thù cho loại DB
        List<String> allFunctions = new ArrayList<>(COMMON_SQL_FUNCTIONS);
        List<String> dbSpecificFunctions = DB_SPECIFIC_FUNCTIONS.getOrDefault(dbType, Collections.emptyList());
        allFunctions.addAll(dbSpecificFunctions);

        for (String function : allFunctions) {
            if (searchTerm.isEmpty() || function.toLowerCase().contains(searchTerm)) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", function);
                item.put("type", "function");
                item.put("description", "SQL Function");
                functions.add(item);
            }
        }

        return functions;
    }
}