package com.carwatch.infrastructure.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

class SqliteConverters {

    // SQLite CURRENT_TIMESTAMP produces "2026-05-19 13:04:29" (space, no T).
    // The app writes ISO "2026-05-19T13:04:29". Handle both on read.
    private static final DateTimeFormatter SQLITE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ReadingConverter
    static class StringToLocalDateTime implements Converter<String, LocalDateTime> {
        @Override
        public LocalDateTime convert(String source) {
            if (source == null || source.isBlank()) return null;
            try {
                return LocalDateTime.parse(source);
            } catch (DateTimeParseException e) {
                return LocalDateTime.parse(source, SQLITE_FORMAT);
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

    // LocalDate: write as ISO "2017-12-01"; on read also handle legacy epoch-ms strings.
    @ReadingConverter
    static class StringToLocalDate implements Converter<String, LocalDate> {
        @Override
        public LocalDate convert(String source) {
            if (source == null || source.isBlank()) return null;
            try {
                return LocalDate.parse(source); // ISO: "2017-12-01"
            } catch (DateTimeParseException e) {
                // Legacy epoch-milliseconds stored by SQLite JDBC before converters were in place
                return Instant.ofEpochMilli(Long.parseLong(source))
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate();
            }
        }
    }

    @WritingConverter
    static class LocalDateToString implements Converter<LocalDate, String> {
        @Override
        public String convert(LocalDate source) {
            return source == null ? null : source.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    // SQLite has no BOOLEAN type — it stores 0/1 as INTEGER.
    @ReadingConverter
    static class IntegerToBoolean implements Converter<Integer, Boolean> {
        @Override
        public Boolean convert(Integer source) {
            return source != null && source != 0;
        }
    }

    @WritingConverter
    static class BooleanToInteger implements Converter<Boolean, Integer> {
        @Override
        public Integer convert(Boolean source) {
            return (source != null && source) ? 1 : 0;
        }
    }
}
