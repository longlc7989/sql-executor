package org.example.sqlexecutor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class SqlLogger extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(SqlLogger.class);
    private static final ThreadLocal<List<String>> SQL_QUERIES = new ThreadLocal<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        SQL_QUERIES.set(new ArrayList<>());

        try {
            filterChain.doFilter(request, response);
        } finally {
            List<String> queries = SQL_QUERIES.get();
            if (queries != null && !queries.isEmpty()) {
                logger.info("SQL Queries executed in this request:");
                for (int i = 0; i < queries.size(); i++) {
                    logger.info("Query #{}: {}", i + 1, queries.get(i));
                }
            }

            SQL_QUERIES.remove();
        }
    }

    public static void logSqlQuery(String query) {
        List<String> queries = SQL_QUERIES.get();
        if (queries != null) {
            queries.add(query);
        }
    }
}