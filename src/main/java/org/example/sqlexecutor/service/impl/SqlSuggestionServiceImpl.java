package org.example.sqlexecutor.service.impl;

import org.example.sqlexecutor.model.SqlSuggestion;
import org.example.sqlexecutor.service.SqlSuggestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Service
public class SqlSuggestionServiceImpl implements SqlSuggestionService {
    private static final Logger logger = LoggerFactory.getLogger(SqlSuggestionServiceImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    // Các từ khóa chung cho tất cả SQL
    private static final List<String> COMMON_SQL_KEYWORDS = Arrays.asList(
            "SELECT", "FROM", "WHERE", "GROUP BY", "HAVING", "ORDER BY",
            "INSERT", "UPDATE", "DELETE", "JOIN", "INNER JOIN", "LEFT JOIN",
            "RIGHT JOIN", "FULL JOIN", "UNION", "CREATE", "ALTER", "DROP",
            "TABLE", "VIEW", "INDEX", "AND", "OR", "NOT", "IN", "BETWEEN",
            "LIKE", "IS NULL", "IS NOT NULL"
    );

    // Các hàm chung cho tất cả SQL
    private static final List<String> COMMON_SQL_FUNCTIONS = Arrays.asList(
            "COUNT", "SUM", "AVG", "MIN", "MAX", "CONCAT", "SUBSTRING",
            "TRIM", "LENGTH", "UPPER", "LOWER", "ROUND"
    );

    // Các từ khóa đặc thù cho từng loại DB
    private static final Map<String, List<String>> DB_SPECIFIC_KEYWORDS = new HashMap<>();
    static {
        // MySQL keywords
        DB_SPECIFIC_KEYWORDS.put("mysql", Arrays.asList(
                "LIMIT", "OFFSET", "SHOW", "DESCRIBE", "EXPLAIN", "USE", "IGNORE"
        ));

        // Oracle keywords
        DB_SPECIFIC_KEYWORDS.put("oracle", Arrays.asList(
                "ROWNUM", "ROWID", "CONNECT BY", "START WITH", "PRIOR", "LEVEL",
                "CONNECT_BY_ROOT", "SYS_CONNECT_BY_PATH", "NOCYCLE"
        ));

        // SQL Server keywords
        DB_SPECIFIC_KEYWORDS.put("sqlserver", Arrays.asList(
                "TOP", "OFFSET FETCH", "OUTPUT", "IDENTITY", "MERGE", "PIVOT", "UNPIVOT",
                "OVER", "PARTITION BY", "WITH TIES", "CROSS APPLY", "OUTER APPLY"
        ));

        // PostgreSQL keywords
        DB_SPECIFIC_KEYWORDS.put("postgresql", Arrays.asList(
                "LIMIT", "OFFSET", "RETURNING", "WITH", "LATERAL", "WINDOW", "OVER",
                "PARTITION BY", "USING"
        ));
    }

    // Các hàm đặc thù cho từng loại DB
    private static final Map<String, List<String>> DB_SPECIFIC_FUNCTIONS = new HashMap<>();
    static {
        // MySQL functions
        DB_SPECIFIC_FUNCTIONS.put("mysql", Arrays.asList(
                "NOW()", "CURDATE()", "CURTIME()", "DATE_FORMAT", "STR_TO_DATE",
                "IFNULL", "IF", "CASE", "GROUP_CONCAT"
        ));

        // Oracle functions
        DB_SPECIFIC_FUNCTIONS.put("oracle", Arrays.asList(
                "SYSDATE", "SYSTIMESTAMP", "NVL", "DECODE", "TO_CHAR", "TO_DATE",
                "TO_NUMBER", "LISTAGG", "REGEXP_LIKE", "REGEXP_REPLACE"
        ));

        // SQL Server functions
        DB_SPECIFIC_FUNCTIONS.put("sqlserver", Arrays.asList(
                "GETDATE()", "GETUTCDATE()", "ISNULL", "COALESCE", "CONVERT", "CAST",
                "DATEADD", "DATEDIFF", "DATENAME", "FORMAT", "STRING_AGG"
        ));

        // PostgreSQL functions
        DB_SPECIFIC_FUNCTIONS.put("postgresql", Arrays.asList(
                "NOW()", "CURRENT_DATE", "CURRENT_TIME", "TO_CHAR", "TO_DATE",
                "COALESCE", "NULLIF", "STRING_AGG", "ARRAY_AGG", "JSONB_AGG"
        ));
    }

    // Chiến lược xử lý metadata cho từng loại DB
    private interface MetadataStrategy {
        List<SqlSuggestion> getTableSuggestions(JdbcTemplate jdbcTemplate, String partial);
        List<SqlSuggestion> getColumnSuggestions(JdbcTemplate jdbcTemplate, String tableName, String partial);
    }

    private final Map<String, MetadataStrategy> metadataStrategies = new HashMap<>();

    public SqlSuggestionServiceImpl() {
        initializeMetadataStrategies();
    }

    private void initializeMetadataStrategies() {
        // MySQL/MariaDB strategy
        metadataStrategies.put("mysql", new MetadataStrategy() {
            @Override
            public List<SqlSuggestion> getTableSuggestions(JdbcTemplate jdbcTemplate, String partial) {
                List<SqlSuggestion> suggestions = new ArrayList<>();
                try {
                    String query = "SELECT TABLE_NAME, TABLE_TYPE, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME LIKE ?";

                    jdbcTemplate.query(query, rs -> {
                        String tableName = rs.getString("TABLE_NAME");
                        String tableType = rs.getString("TABLE_TYPE");
                        String comment = rs.getString("TABLE_COMMENT");

                        suggestions.add(new SqlSuggestion("table", tableName,
                                comment != null && !comment.isEmpty() ? comment : tableType));

                    }, "%" + partial + "%");
                } catch (Exception e) {
                    // Fallback to simpler query
                    String query = "SHOW TABLES LIKE ?";
                    jdbcTemplate.query(query, rs -> {
                        String tableName = rs.getString(1);
                        suggestions.add(new SqlSuggestion("table", tableName, "Table"));
                    }, "%" + partial + "%");
                }
                return suggestions;
            }

            @Override
            public List<SqlSuggestion> getColumnSuggestions(JdbcTemplate jdbcTemplate, String tableName, String partial) {
                List<SqlSuggestion> suggestions = new ArrayList<>();
                try {
                    String query = "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT " +
                            "FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME = ? " +
                            "AND COLUMN_NAME LIKE ?";

                    jdbcTemplate.query(query, rs -> {
                        String columnName = rs.getString("COLUMN_NAME");
                        String dataType = rs.getString("DATA_TYPE");
                        String comment = rs.getString("COLUMN_COMMENT");

                        SqlSuggestion suggestion = new SqlSuggestion("column", columnName,
                                (comment != null && !comment.isEmpty()) ? comment : dataType);
                        suggestion.setCategory(tableName);
                        suggestions.add(suggestion);

                    }, tableName, "%" + partial + "%");
                } catch (Exception e) {
                    // Fallback to DESCRIBE
                    String query = "DESCRIBE " + tableName;
                    jdbcTemplate.query(query, rs -> {
                        String columnName = rs.getString("Field");
                        String dataType = rs.getString("Type");

                        if (columnName.toUpperCase().contains(partial.toUpperCase())) {
                            SqlSuggestion suggestion = new SqlSuggestion("column", columnName, dataType);
                            suggestion.setCategory(tableName);
                            suggestions.add(suggestion);
                        }
                    });
                }
                return suggestions;
            }
        });

        // Oracle strategy
        metadataStrategies.put("oracle", new MetadataStrategy() {
            @Override
            public List<SqlSuggestion> getTableSuggestions(JdbcTemplate jdbcTemplate, String partial) {
                List<SqlSuggestion> suggestions = new ArrayList<>();
                try {
                    String query = "SELECT TABLE_NAME, 'TABLE' AS TABLE_TYPE, COMMENTS " +
                            "FROM ALL_TAB_COMMENTS " +
                            "WHERE OWNER = USER " +
                            "AND TABLE_NAME LIKE ? " +
                            "ORDER BY TABLE_NAME";

                    jdbcTemplate.query(query, rs -> {
                        String tableName = rs.getString("TABLE_NAME");
                        String comment = rs.getString("COMMENTS");

                        suggestions.add(new SqlSuggestion("table", tableName,
                                comment != null && !comment.isEmpty() ? comment : "Table"));

                    }, "%" + partial.toUpperCase() + "%");
                } catch (Exception e) {
                    // Fallback to simpler query
                    String query = "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = USER AND TABLE_NAME LIKE ?";
                    jdbcTemplate.query(query, rs -> {
                        String tableName = rs.getString("TABLE_NAME");
                        suggestions.add(new SqlSuggestion("table", tableName, "Table"));
                    }, "%" + partial.toUpperCase() + "%");
                }
                return suggestions;
            }

            @Override
            public List<SqlSuggestion> getColumnSuggestions(JdbcTemplate jdbcTemplate, String tableName, String partial) {
                List<SqlSuggestion> suggestions = new ArrayList<>();
                try {
                    String query = "SELECT COLUMN_NAME, DATA_TYPE, COMMENTS " +
                            "FROM ALL_COL_COMMENTS c " +
                            "JOIN ALL_TAB_COLUMNS t ON c.OWNER = t.OWNER AND c.TABLE_NAME = t.TABLE_NAME AND c.COLUMN_NAME = t.COLUMN_NAME " +
                            "WHERE c.OWNER = USER " +
                            "AND c.TABLE_NAME = ? " +
                            "AND c.COLUMN_NAME LIKE ? " +
                            "ORDER BY COLUMN_ID";

                    jdbcTemplate.query(query, rs -> {
                        String columnName = rs.getString("COLUMN_NAME");
                        String dataType = rs.getString("DATA_TYPE");
                        String comment = rs.getString("COMMENTS");

                        SqlSuggestion suggestion = new SqlSuggestion("column", columnName,
                                (comment != null && !comment.isEmpty()) ? comment : dataType);
                        suggestion.setCategory(tableName);
                        suggestions.add(suggestion);

                    }, tableName.toUpperCase(), "%" + partial.toUpperCase() + "%");
                } catch (Exception e) {
                    // Fallback to simpler query
                    String query = "SELECT COLUMN_NAME, DATA_TYPE FROM ALL_TAB_COLUMNS " +
                            "WHERE OWNER = USER AND TABLE_NAME = ? AND COLUMN_NAME LIKE ?";
                    jdbcTemplate.query(query, rs -> {
                        String columnName = rs.getString("COLUMN_NAME");
                        String dataType = rs.getString("DATA_TYPE");

                        SqlSuggestion suggestion = new SqlSuggestion("column", columnName, dataType);
                        suggestion.setCategory(tableName);
                        suggestions.add(suggestion);
                    }, tableName.toUpperCase(), "%" + partial.toUpperCase() + "%");
                }
                return suggestions;
            }
        });

        // SQL Server strategy
        metadataStrategies.put("sqlserver", new MetadataStrategy() {
            @Override
            public List<SqlSuggestion> getTableSuggestions(JdbcTemplate jdbcTemplate, String partial) {
                List<SqlSuggestion> suggestions = new ArrayList<>();
                try {
                    String query = "SELECT t.name AS TABLE_NAME, " +
                            "CASE WHEN t.type = 'U' THEN 'TABLE' ELSE 'VIEW' END AS TABLE_TYPE, " +
                            "ep.value AS DESCRIPTION " +
                            "FROM sys.tables t " +
                            "LEFT JOIN sys.extended_properties ep ON ep.major_id = t.object_id AND ep.minor_id = 0 AND ep.name = 'MS_Description' " +
                            "WHERE t.name LIKE ? " +
                            "ORDER BY t.name";

                    jdbcTemplate.query(query, rs -> {
                        String tableName = rs.getString("TABLE_NAME");
                        String tableType = rs.getString("TABLE_TYPE");
                        String description = rs.getString("DESCRIPTION");

                        suggestions.add(new SqlSuggestion("table", tableName,
                                description != null ? description : tableType));

                    }, "%" + partial + "%");
                } catch (Exception e) {
                    // Fallback to simpler query
                    String query = "SELECT name FROM sys.tables WHERE name LIKE ?";
                    jdbcTemplate.query(query, rs -> {
                        String tableName = rs.getString("name");
                        suggestions.add(new SqlSuggestion("table", tableName, "Table"));
                    }, "%" + partial + "%");
                }
                return suggestions;
            }

            @Override
            public List<SqlSuggestion> getColumnSuggestions(JdbcTemplate jdbcTemplate, String tableName, String partial) {
                List<SqlSuggestion> suggestions = new ArrayList<>();
                try {
                    String query = "SELECT c.name AS COLUMN_NAME, " +
                            "t.name AS DATA_TYPE, " +
                            "ep.value AS DESCRIPTION " +
                            "FROM sys.columns c " +
                            "JOIN sys.types t ON c.user_type_id = t.user_type_id " +
                            "JOIN sys.tables tbl ON c.object_id = tbl.object_id " +
                            "LEFT JOIN sys.extended_properties ep ON ep.major_id = c.object_id AND ep.minor_id = c.column_id AND ep.name = 'MS_Description' " +
                            "WHERE tbl.name = ? AND c.name LIKE ? " +
                            "ORDER BY c.column_id";

                    jdbcTemplate.query(query, rs -> {
                        String columnName = rs.getString("COLUMN_NAME");
                        String dataType = rs.getString("DATA_TYPE");
                        String description = rs.getString("DESCRIPTION");

                        SqlSuggestion suggestion = new SqlSuggestion("column", columnName,
                                description != null ? description : dataType);
                        suggestion.setCategory(tableName);
                        suggestions.add(suggestion);

                    }, tableName, "%" + partial + "%");
                } catch (Exception e) {
                    // Fallback to simpler query
                    String query = "SELECT c.name AS COLUMN_NAME, t.name AS DATA_TYPE " +
                            "FROM sys.columns c " +
                            "JOIN sys.types t ON c.user_type_id = t.user_type_id " +
                            "JOIN sys.tables tbl ON c.object_id = tbl.object_id " +
                            "WHERE tbl.name = ? AND c.name LIKE ?";
                    jdbcTemplate.query(query, rs -> {
                        String columnName = rs.getString("COLUMN_NAME");
                        String dataType = rs.getString("DATA_TYPE");

                        SqlSuggestion suggestion = new SqlSuggestion("column", columnName, dataType);
                        suggestion.setCategory(tableName);
                        suggestions.add(suggestion);
                    }, tableName, "%" + partial + "%");
                }
                return suggestions;
            }
        });

        // PostgreSQL strategy
        metadataStrategies.put("postgresql", new MetadataStrategy() {
            @Override
            public List<SqlSuggestion> getTableSuggestions(JdbcTemplate jdbcTemplate, String partial) {
                List<SqlSuggestion> suggestions = new ArrayList<>();
                try {
                    String query = "SELECT table_name, table_type, obj_description(c.oid, 'pg_class') as description " +
                            "FROM information_schema.tables t " +
                            "JOIN pg_catalog.pg_class c ON c.relname = t.table_name " +
                            "WHERE table_schema = current_schema() " +
                            "AND table_name LIKE ? " +
                            "ORDER BY table_name";

                    jdbcTemplate.query(query, rs -> {
                        String tableName = rs.getString("table_name");
                        String tableType = rs.getString("table_type");
                        String description = rs.getString("description");

                        suggestions.add(new SqlSuggestion("table", tableName,
                                description != null ? description : tableType));

                    }, "%" + partial + "%");
                } catch (Exception e) {
                    // Fallback to simpler query
                    String query = "SELECT tablename FROM pg_tables WHERE schemaname = current_schema() AND tablename LIKE ?";
                    jdbcTemplate.query(query, rs -> {
                        String tableName = rs.getString("tablename");
                        suggestions.add(new SqlSuggestion("table", tableName, "Table"));
                    }, "%" + partial + "%");
                }
                return suggestions;
            }

            @Override
            public List<SqlSuggestion> getColumnSuggestions(JdbcTemplate jdbcTemplate, String tableName, String partial) {
                List<SqlSuggestion> suggestions = new ArrayList<>();
                try {
                    String query = "SELECT c.column_name, c.data_type, " +
                            "pg_catalog.col_description(format('%s.%I', c.table_schema, c.table_name)::regclass::oid, c.ordinal_position) as description " +
                            "FROM information_schema.columns c " +
                            "WHERE c.table_schema = current_schema() " +
                            "AND c.table_name = ? " +
                            "AND c.column_name LIKE ? " +
                            "ORDER BY c.ordinal_position";

                    jdbcTemplate.query(query, rs -> {
                        String columnName = rs.getString("column_name");
                        String dataType = rs.getString("data_type");
                        String description = rs.getString("description");

                        SqlSuggestion suggestion = new SqlSuggestion("column", columnName,
                                description != null ? description : dataType);
                        suggestion.setCategory(tableName);
                        suggestions.add(suggestion);

                    }, tableName, "%" + partial + "%");
                } catch (Exception e) {
                    // Fallback to simpler query
                    String query = "SELECT column_name, data_type FROM information_schema.columns " +
                            "WHERE table_schema = current_schema() AND table_name = ? AND column_name LIKE ?";
                    jdbcTemplate.query(query, rs -> {
                        String columnName = rs.getString("column_name");
                        String dataType = rs.getString("data_type");

                        SqlSuggestion suggestion = new SqlSuggestion("column", columnName, dataType);
                        suggestion.setCategory(tableName);
                        suggestions.add(suggestion);
                    }, tableName, "%" + partial + "%");
                }
                return suggestions;
            }
        });
    }

    private String getDatabaseType() {
        String dbType = "mysql"; // Mặc định

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String databaseProductName = metaData.getDatabaseProductName().toLowerCase();

            if (databaseProductName.contains("mysql") || databaseProductName.contains("mariadb")) {
                dbType = "mysql";
            } else if (databaseProductName.contains("oracle")) {
                dbType = "oracle";
            } else if (databaseProductName.contains("microsoft") || databaseProductName.contains("sql server")) {
                dbType = "sqlserver";
            } else if (databaseProductName.contains("postgresql")) {
                dbType = "postgresql";
            }

            logger.info("Detected database type: {}", dbType);

        } catch (SQLException e) {
            logger.error("Error determining database type", e);
        }

        return dbType;
    }

    @Override
    public List<SqlSuggestion> getSuggestions(String partial, String context) {
        // Phân tích ngữ cảnh để xác định loại gợi ý
        if (isAfterFrom(context) || isAfterJoin(context)) {
            return getTableSuggestions(partial);
        } else if (isAfterSelect(context) || isAfterWhere(context)) {
            // Lấy tên bảng từ ngữ cảnh để gợi ý cột
            String tableName = extractTableName(context);
            if (tableName != null) {
                return getColumnSuggestions(tableName, partial);
            }
        }

        // Mặc định: kết hợp tất cả loại gợi ý
        List<SqlSuggestion> allSuggestions = new ArrayList<>();
        allSuggestions.addAll(getKeywordSuggestions(partial));
        allSuggestions.addAll(getFunctionSuggestions(partial));
        allSuggestions.addAll(getTableSuggestions(partial));

        // Nếu ngữ cảnh chứa FROM, thêm gợi ý cột cho tất cả bảng
        if (context.toUpperCase().contains("FROM")) {
            List<String> tables = extractAllTableNames(context);
            for (String table : tables) {
                allSuggestions.addAll(getColumnSuggestions(table, partial));
            }
        }

        return allSuggestions.stream()
                .filter(s -> s.getName().toUpperCase().contains(partial.toUpperCase()))
                .collect(Collectors.toList());
    }

    @Override
    public List<SqlSuggestion> getTableSuggestions(String partial) {
        String dbType = getDatabaseType();
        MetadataStrategy strategy = metadataStrategies.getOrDefault(dbType, metadataStrategies.get("mysql"));

        try {
            return strategy.getTableSuggestions(jdbcTemplate, partial);
        } catch (Exception e) {
            logger.error("Error getting table suggestions", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<SqlSuggestion> getColumnSuggestions(String tableName, String partial) {
        String dbType = getDatabaseType();
        MetadataStrategy strategy = metadataStrategies.getOrDefault(dbType, metadataStrategies.get("mysql"));

        try {
            return strategy.getColumnSuggestions(jdbcTemplate, tableName, partial);
        } catch (Exception e) {
            logger.error("Error getting column suggestions for table: " + tableName, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<SqlSuggestion> getKeywordSuggestions(String partial) {
        String dbType = getDatabaseType();

        // Kết hợp từ khóa chung và từ khóa đặc thù
        List<String> keywords = new ArrayList<>(COMMON_SQL_KEYWORDS);
        List<String> dbSpecificKeywords = DB_SPECIFIC_KEYWORDS.getOrDefault(dbType, new ArrayList<>());
        keywords.addAll(dbSpecificKeywords);

        return keywords.stream()
                .filter(keyword -> keyword.toUpperCase().contains(partial.toUpperCase()))
                .map(keyword -> new SqlSuggestion("keyword", keyword, "SQL Keyword"))
                .collect(Collectors.toList());
    }

    @Override
    public List<SqlSuggestion> getFunctionSuggestions(String partial) {
        String dbType = getDatabaseType();

        // Kết hợp hàm chung và hàm đặc thù
        List<String> functions = new ArrayList<>(COMMON_SQL_FUNCTIONS);
        List<String> dbSpecificFunctions = DB_SPECIFIC_FUNCTIONS.getOrDefault(dbType, new ArrayList<>());
        functions.addAll(dbSpecificFunctions);

        return functions.stream()
                .filter(function -> function.toUpperCase().contains(partial.toUpperCase()))
                .map(function -> new SqlSuggestion("function", function, "SQL Function"))
                .collect(Collectors.toList());
    }

    // Helper methods để phân tích ngữ cảnh SQL

    private boolean isAfterFrom(String context) {
        Pattern pattern = Pattern.compile("FROM\\s+$", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(context).find();
    }

    private boolean isAfterJoin(String context) {
        Pattern pattern = Pattern.compile("JOIN\\s+$", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(context).find();
    }

    private boolean isAfterSelect(String context) {
        Pattern pattern = Pattern.compile("SELECT\\s+$", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(context).find();
    }

    private boolean isAfterWhere(String context) {
        Pattern pattern = Pattern.compile("WHERE\\s+$", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(context).find();
    }

    private String extractTableName(String context) {
        Pattern pattern = Pattern.compile("FROM\\s+([\\w_]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(context);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private List<String> extractAllTableNames(String context) {
        List<String> tables = new ArrayList<>();
        Pattern pattern = Pattern.compile("FROM\\s+([\\w_]+)|JOIN\\s+([\\w_]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(context);

        while (matcher.find()) {
            String table = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            tables.add(table);
        }

        return tables;
    }
}