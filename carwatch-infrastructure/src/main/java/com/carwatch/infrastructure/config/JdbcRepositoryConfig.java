package com.carwatch.infrastructure.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

@Configuration
@EnableJdbcRepositories(basePackages = "com.carwatch.infrastructure.persistence")
public class JdbcRepositoryConfig {

    @Bean
    JdbcDialect jdbcDialect() {
        return SqliteDialect.INSTANCE;
    }

    @Bean
    JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(List.of(
            new SqliteConverters.StringToLocalDateTime(),
            new SqliteConverters.LocalDateTimeToString(),
            new SqliteConverters.IntegerToBoolean(),
            new SqliteConverters.BooleanToInteger()
        ));
    }
}
