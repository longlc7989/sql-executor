package org.example.sqlexecutor.service.impl;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.example.sqlexecutor.config.SqlLogger;
import org.example.sqlexecutor.service.ExcelExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ExcelExportServiceImpl implements ExcelExportService {
    private static final Logger logger = LoggerFactory.getLogger(ExcelExportServiceImpl.class);
    private static final int BATCH_SIZE = 100;

    @Autowired
    private DataSource dataSource;

    @Override
    public void exportToExcel(String query, HttpServletResponse response) throws SQLException {
        logger.info("Starting Excel export for query: {}", query);
        SqlLogger.logSqlQuery("EXCEL EXPORT: " + query);

        // Tạo tên file với timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "sql_export_" + timestamp + ".xlsx";

        // Thiết lập response headers
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);

        // Sử dụng SXSSFWorkbook để giảm memory footprint
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query);
             SXSSFWorkbook workbook = new SXSSFWorkbook(100)) { // Chỉ giữ 100 rows trong memory

            workbook.setCompressTempFiles(true);
            SXSSFSheet sheet = workbook.createSheet("Data");

            // Lấy metadata
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Tạo header row
            Row headerRow = sheet.createRow(0);
            for (int i = 1; i <= columnCount; i++) {
                Cell cell = headerRow.createCell(i - 1);
                cell.setCellValue(metaData.getColumnName(i));
            }

            // Process data row-by-row để giảm memory usage
            int rowIndex = 1;
            while (rs.next()) {
                Row dataRow = sheet.createRow(rowIndex);

                for (int i = 1; i <= columnCount; i++) {
                    Cell cell = dataRow.createCell(i - 1);
                    Object value = rs.getObject(i);

                    if (value == null) {
                        cell.setCellValue("");
                    } else {
                        setCellValueBasedOnType(cell, value);
                    }
                }

                rowIndex++;

                // Auto-flush to disk
                if (rowIndex % BATCH_SIZE == 0) {
                    ((SXSSFSheet)sheet).flushRows(BATCH_SIZE);
                    logger.debug("Flushed {} rows to disk", BATCH_SIZE);
                }
            }

            // Ghi workbook ra response output stream
            workbook.write(response.getOutputStream());

            logger.info("Excel export completed: {} rows", rowIndex - 1);
        } catch (IOException e) {
            logger.error("Error exporting to Excel", e);
            throw new RuntimeException("Failed to export Excel file", e);
        }
    }

    private void setCellValueBasedOnType(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            if (value instanceof Double || value instanceof Float) {
                cell.setCellValue(((Number) value).doubleValue());
            } else {
                cell.setCellValue(((Number) value).longValue());
            }
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof java.sql.Date) {
            cell.setCellValue(((java.sql.Date) value).toLocalDate().toString());
        } else if (value instanceof java.sql.Timestamp) {
            cell.setCellValue(((java.sql.Timestamp) value).toLocalDateTime().toString());
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
