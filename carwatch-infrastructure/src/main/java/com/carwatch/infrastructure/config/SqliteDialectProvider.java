package com.carwatch.infrastructure.config;

import java.util.Optional;
import org.springframework.data.jdbc.repository.config.DialectResolver;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;

public class SqliteDialectProvider implements DialectResolver.JdbcDialectProvider {

    @Override
    public Optional<org.springframework.data.relational.core.dialect.Dialect> getDialect(JdbcOperations operations) {
        return Optional.ofNullable(
                operations.execute((ConnectionCallback<org.springframework.data.relational.core.dialect.Dialect>) connection -> {
                    String productName = connection.getMetaData().getDatabaseProductName();
                    if (productName.toLowerCase().contains("sqlite")) {
                        return SqliteDialect.INSTANCE;
                    }
                    return null;
                })
        );
    }
}
