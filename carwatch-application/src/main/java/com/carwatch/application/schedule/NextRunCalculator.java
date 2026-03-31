package com.carwatch.application.schedule;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class NextRunCalculator {

    private final Clock clock;

    public NextRunCalculator() {
        this(Clock.systemDefaultZone());
    }

    NextRunCalculator(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime calculateNext(String cronExpression, String zoneId) {
        ZoneId zone = ZoneId.of(zoneId);
        Instant nowInstant = Instant.now(clock);
        ZonedDateTime now = nowInstant.atZone(zone);
        ZonedDateTime next = CronExpression.parse(cronExpression).next(now);
        if (next == null) {
            throw new IllegalArgumentException("Unable to calculate next run for cron expression: " + cronExpression);
        }
        return next.toLocalDateTime();
    }
}
