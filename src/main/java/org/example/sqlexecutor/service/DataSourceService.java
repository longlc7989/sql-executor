package org.example.sqlexecutor.service;

import org.example.sqlexecutor.adapter.SqlDatabaseAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DataSourceService {
    @Autowired
    private Map<String, DataSource> dataSourceMap;

    @Autowired
    private SqlDatabaseAdapter databaseAdapter;

    public List<String> getAvailableDataSources() {
        return new ArrayList<>(dataSourceMap.keySet());
    }

    public DataSource getDataSource(String name) {
        return dataSourceMap.getOrDefault(name, dataSourceMap.get("mysql"));
    }

    public JdbcTemplate getJdbcTemplate(String dataSourceName) {
        DataSource dataSource = getDataSource(dataSourceName);
        return new JdbcTemplate(dataSource);
    }

    public String getDatabaseType(String dataSourceName) {
        JdbcTemplate jdbcTemplate = getJdbcTemplate(dataSourceName);
        try {
            String dbProductName = jdbcTemplate.getDataSource().getConnection().getMetaData()
                    .getDatabaseProductName().toLowerCase();

            if (dbProductName.contains("mysql") || dbProductName.contains("mariadb")) {
                return "mysql";
            } else if (dbProductName.contains("microsoft") || dbProductName.contains("sql server")) {
                return "sqlserver";
            } else if (dbProductName.contains("oracle")) {
                return "oracle";
            }
            return "mysql"; // Default
        } catch (Exception e) {
            return "mysql"; // Default on error
        }
    }

    public SqlDatabaseAdapter.DatabaseStrategy getDatabaseStrategy(String dataSourceName) {
        JdbcTemplate jdbcTemplate = getJdbcTemplate(dataSourceName);
        return databaseAdapter.getStrategy(jdbcTemplate);
    }

    public String getDataSourceInfo(String name) {
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate(name);
            String query = databaseAdapter.getStrategy(jdbcTemplate).getTableInfoQuery();
            return jdbcTemplate.queryForObject(
                    "SELECT DATABASE() as db", String.class);
        } catch (Exception e) {
            return name;
        }
    }
}
