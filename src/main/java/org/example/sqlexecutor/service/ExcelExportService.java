package org.example.sqlexecutor.service;

import jakarta.servlet.http.HttpServletResponse;

import java.sql.SQLException;

public interface ExcelExportService {
    void exportToExcel(String query, HttpServletResponse response) throws SQLException;
}
