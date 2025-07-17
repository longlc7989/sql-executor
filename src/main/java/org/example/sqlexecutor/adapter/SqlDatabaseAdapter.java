package org.example.sqlexecutor.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Component
public class SqlDatabaseAdapter {
    private final Map<String, DatabaseStrategy> strategies = new HashMap<>();

    public SqlDatabaseAdapter() {
        strategies.put("mysql", new MySqlStrategy());
        strategies.put("sqlserver", new SqlServerStrategy());
        strategies.put("oracle", new OracleStrategy());
    }

    public DatabaseStrategy getStrategy(JdbcTemplate jdbcTemplate) {
        try {
            DatabaseMetaData metaData = jdbcTemplate.getDataSource().getConnection().getMetaData();
            String databaseProductName = metaData.getDatabaseProductName().toLowerCase();

            if (databaseProductName.contains("mysql") || databaseProductName.contains("mariadb")) {
                return strategies.get("mysql");
            } else if (databaseProductName.contains("microsoft") || databaseProductName.contains("sql server")) {
                return strategies.get("sqlserver");
            } else if (databaseProductName.contains("oracle")) {
                return strategies.get("oracle");
            }
        } catch (SQLException e) {
            // Fallback to MySQL
        }
        return strategies.get("mysql");
    }

    public interface DatabaseStrategy {
        String getPaginatedQuery(String query, int offset, int limit);
        String getTableListQuery();
        String getColumnListQuery(String tableName);
        String getTableInfoQuery();
    }

    private static class MySqlStrategy implements DatabaseStrategy {
        @Override
        public String getPaginatedQuery(String query, int offset, int limit) {
            return query + " LIMIT " + limit + " OFFSET " + offset;
        }

        @Override
        public String getTableListQuery() {
            return "SHOW TABLES";
        }

        @Override
        public String getColumnListQuery(String tableName) {
            return "SHOW COLUMNS FROM " + tableName;
        }

        @Override
        public String getTableInfoQuery() {
            return "SELECT TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE()";
        }
    }

    private static class SqlServerStrategy implements DatabaseStrategy {
        @Override
        public String getPaginatedQuery(String query, int offset, int limit) {
            return query + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
        }

        @Override
        public String getTableListQuery() {
            return "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE'";
        }

        @Override
        public String getColumnListQuery(String tableName) {
            return "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + tableName + "'";
        }

        @Override
        public String getTableInfoQuery() {
            return "SELECT t.name AS TABLE_NAME, ep.value AS TABLE_COMMENT FROM sys.tables t " +
                    "LEFT JOIN sys.extended_properties ep ON ep.major_id = t.object_id AND ep.minor_id = 0 AND ep.name = 'MS_Description'";
        }
    }

    private static class OracleStrategy implements DatabaseStrategy {
        @Override
        public String getPaginatedQuery(String query, int offset, int limit) {
            return "SELECT * FROM (SELECT a.*, ROWNUM rnum FROM (" + query +
                    ") a WHERE ROWNUM <= " + (offset + limit) + ") WHERE rnum > " + offset;
        }

        @Override
        public String getTableListQuery() {
            return "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = USER";
        }

        @Override
        public String getColumnListQuery(String tableName) {
            return "SELECT COLUMN_NAME, DATA_TYPE FROM ALL_TAB_COLUMNS WHERE TABLE_NAME = '" +
                    tableName + "' AND OWNER = USER";
        }

        @Override
        public String getTableInfoQuery() {
            return "SELECT TABLE_NAME, COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER = USER";
        }
    }
}
