package org.example.sqlexecutor.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.example.sqlexecutor.exception.SqlExecutionException;
import org.example.sqlexecutor.model.SqlQuery;
import org.example.sqlexecutor.model.SqlResult;
import org.example.sqlexecutor.service.ExcelExportService;
import org.example.sqlexecutor.service.SqlExecutorService;
import org.example.sqlexecutor.validator.ConfirmationValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sql")
@CrossOrigin(origins = "*")
public class SqlController {

    @Autowired
    private ExcelExportService excelExportService;

    @Autowired
    private SqlExecutorService sqlExecutorService;

    @Autowired
    private ConfirmationValidator confirmationValidator;

    @PostMapping("/execute")
    public ResponseEntity<?> executeSql(@RequestBody SqlQuery sqlQuery) {
        try {
            // Xác định loại truy vấn
            String queryType = sqlExecutorService.determineQueryType(sqlQuery.getQuery().trim());

            // Xác thực mã xác nhận
            if (!confirmationValidator.validateConfirmationCode(sqlQuery.getConfirmationCode(), queryType)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new SqlResult() {{
                            setSuccess(false);
                            setMessage("Invalid confirmation code. Please try again.");
                        }});
            }

            SqlResult result = sqlExecutorService.executeSql(sqlQuery);
            return ResponseEntity.ok(result);
        } catch (SqlExecutionException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new SqlResult() {{
                        setSuccess(false);
                        setMessage(e.getMessage());
                    }});
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new SqlResult() {{
                        setSuccess(false);
                        setMessage("An unexpected error occurred: " + e.getMessage());
                    }});
        }
    }

    @GetMapping("/export-excel")
    public void exportToExcel(@RequestParam String query,
                              @RequestParam String confirmationCode,
                              HttpServletResponse response) {
        try {
            // Xác định loại truy vấn
            String queryType = sqlExecutorService.determineQueryType(query.trim());

            // Xác thực mã xác nhận
            if (!confirmationValidator.validateConfirmationCode(confirmationCode, queryType)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Invalid confirmation code");
                return;
            }

            // Xuất Excel chỉ cho truy vấn SELECT
            if (!"SELECT".equals(queryType)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Only SELECT queries can be exported to Excel");
                return;
            }

            excelExportService.exportToExcel(query, response);

        } catch (Exception e) {
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("Error exporting to Excel: " + e.getMessage());
            } catch (Exception ex) {
                // Ignore
            }
        }
    }
}