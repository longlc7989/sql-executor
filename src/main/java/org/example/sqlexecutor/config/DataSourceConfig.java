package org.example.sqlexecutor.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {
    // MySQL DataSource
    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.mysql")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "mysqlDataSource")
    public DataSource mysqlDataSource() {
        return mysqlDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

//    // SQL Server DataSource
//    @Bean
//    @ConfigurationProperties("spring.datasource.sqlserver")
//    public DataSourceProperties sqlServerDataSourceProperties() {
//        return new DataSourceProperties();
//    }
//
//    @Bean(name = "sqlServerDataSource")
//    public DataSource sqlServerDataSource() {
//        return sqlServerDataSourceProperties()
//                .initializeDataSourceBuilder()
//                .type(HikariDataSource.class)
//                .build();
//    }
//
//    // Oracle DataSource
//    @Bean
//    @ConfigurationProperties("spring.datasource.oracle")
//    public DataSourceProperties oracleDataSourceProperties() {
//        return new DataSourceProperties();
//    }
//
//    @Bean(name = "oracleDataSource")
//    public DataSource oracleDataSource() {
//        return oracleDataSourceProperties()
//                .initializeDataSourceBuilder()
//                .type(HikariDataSource.class)
//                .build();
//    }

    // Map of all DataSources
    @Bean
    public Map<String, DataSource> dataSourceMap() {
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        dataSourceMap.put("mysql", mysqlDataSource());

//        // Thêm điều kiện kiểm tra để tránh lỗi khi không có cấu hình
//        try {
//            dataSourceMap.put("sqlserver", sqlServerDataSource());
//        } catch (Exception e) {
//            // Ignore if not configured
//        }
//
//        try {
//            dataSourceMap.put("oracle", oracleDataSource());
//        } catch (Exception e) {
//            // Ignore if not configured
//        }

        return dataSourceMap;
    }
}
