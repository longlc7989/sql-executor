package org.example.sqlexecutor.service;

import org.example.sqlexecutor.model.SqlSuggestion;

import java.util.List;

public interface SqlSuggestionService {
    List<SqlSuggestion> getSuggestions(String partial, String context);
    List<SqlSuggestion> getTableSuggestions(String partial);
    List<SqlSuggestion> getColumnSuggestions(String tableName, String partial);
    List<SqlSuggestion> getKeywordSuggestions(String partial);
    List<SqlSuggestion> getFunctionSuggestions(String partial);
}