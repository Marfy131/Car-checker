package com.carwatch.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

@Configuration
@EnableJdbcRepositories(basePackages = "com.carwatch.infrastructure.persistence")
public class JdbcRepositoryConfig {
}
