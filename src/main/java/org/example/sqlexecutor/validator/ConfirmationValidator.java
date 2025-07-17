package org.example.sqlexecutor.validator;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class ConfirmationValidator {

    public boolean validateConfirmationCode(String code, String queryType) {
        if (code == null || code.length() != 8) {
            return false;
        }

        // Kiểm tra chỉ chứa các chữ số
        if (!code.matches("\\d{8}")) {
            return false;
        }

        // Lấy ngày hiện tại
        LocalDate today = LocalDate.now();

        // Định dạng khác nhau dựa trên loại truy vấn
        String expectedCode;
        if ("SELECT".equalsIgnoreCase(queryType)) {
            // DDMMYYYY cho SELECT
            expectedCode = today.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
        } else {
            // YYYYMMDD cho các lệnh khác
            expectedCode = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }

        return code.equals(expectedCode);
    }
}