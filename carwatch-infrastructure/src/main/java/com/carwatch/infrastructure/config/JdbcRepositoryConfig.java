package com.carwatch.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

@Configuration
@EnableJdbcRepositories(basePackages = "com.carwatch.infrastructure.persistence")
public class JdbcRepositoryConfig {

    @Bean
    JdbcDialect jdbcDialect() {
        return SqliteDialect.INSTANCE;
    }
}
