package org.example.sqlexecutor.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sql-history")
@CrossOrigin(origins = "*")
public class SqlHistoryController {

    private static final List<Map<String, Object>> SQL_HISTORY = new ArrayList<>();
    private static final int MAX_HISTORY = 100;

    @GetMapping
    public List<Map<String, Object>> getHistory() {
        return SQL_HISTORY;
    }

    public static void addToHistory(String query, boolean success, String message, long executionTime) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("query", query);
        entry.put("timestamp", LocalDateTime.now().toString());
        entry.put("status", success ? "success" : "error");
        entry.put("message", message);
        entry.put("executionTime", executionTime);

        SQL_HISTORY.add(0, entry);

        if (SQL_HISTORY.size() > MAX_HISTORY) {
            SQL_HISTORY.remove(SQL_HISTORY.size() - 1);
        }
    }
}