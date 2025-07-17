package org.example.sqlexecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class SqlexecutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SqlexecutorApplication.class, args);
    }

}
