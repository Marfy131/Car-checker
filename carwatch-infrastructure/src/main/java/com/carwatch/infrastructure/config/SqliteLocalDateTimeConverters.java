package com.carwatch.infrastructure.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

class SqliteLocalDateTimeConverters {

    // SQLite CURRENT_TIMESTAMP produces "2026-05-19 13:04:29" (space, no T).
    // App writes ISO format "2026-05-19T13:04:29". Handle both on read.
    private static final DateTimeFormatter SQLITE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ReadingConverter
    static class StringToLocalDateTime implements Converter<String, LocalDateTime> {
        @Override
        public LocalDateTime convert(String source) {
            if (source == null || source.isBlank()) return null;
            try {
                return LocalDateTime.parse(source); // ISO: "2026-05-19T13:04:29"
            } catch (DateTimeParseException e) {
                return LocalDateTime.parse(source, SQLITE_FORMAT); // "2026-05-19 13:04:29"
            }
        }
    }

    @WritingConverter
    static class LocalDateTimeToString implements Converter<LocalDateTime, String> {
        @Override
        public String convert(LocalDateTime source) {
            return source == null ? null : source.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}
