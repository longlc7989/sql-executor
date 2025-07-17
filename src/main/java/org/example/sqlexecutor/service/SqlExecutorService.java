package org.example.sqlexecutor.service;

import org.example.sqlexecutor.model.SqlQuery;
import org.example.sqlexecutor.model.SqlResult;
import org.springframework.jdbc.core.JdbcTemplate;

public interface SqlExecutorService {
    SqlResult executeSql(SqlQuery sqlQuery);
    long countRecords(JdbcTemplate jdbcTemplate, String query);
    String determineQueryType(String query);
}