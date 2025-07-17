package org.example.sqlexecutor.controller;

import org.example.sqlexecutor.service.DataSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasources")
@CrossOrigin(origins = "*")
public class DataSourceController {

    @Autowired
    private DataSourceService dataSourceService;

    @GetMapping
    public List<Map<String, String>> getDataSources() {
        List<String> datasourceNames = dataSourceService.getAvailableDataSources();
        List<Map<String, String>> result = new ArrayList<>();

        for (String name : datasourceNames) {
            Map<String, String> datasource = new HashMap<>();
            datasource.put("name", name);

            // Lấy thông tin chi tiết của database
            String dbInfo = dataSourceService.getDataSourceInfo(name);
            String dbType = dataSourceService.getDatabaseType(name);

            datasource.put("description", dbInfo);
            datasource.put("type", dbType);
            result.add(datasource);
        }

        return result;
    }
}