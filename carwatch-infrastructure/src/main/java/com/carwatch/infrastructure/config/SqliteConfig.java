package com.carwatch.infrastructure.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SqliteConfig {

    @Bean
    CommandLineRunner sqlitePragmas(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("PRAGMA journal_mode=WAL;");
            jdbcTemplate.execute("PRAGMA foreign_keys=ON;");
            jdbcTemplate.execute("PRAGMA busy_timeout=5000;");
        };
    }
}
