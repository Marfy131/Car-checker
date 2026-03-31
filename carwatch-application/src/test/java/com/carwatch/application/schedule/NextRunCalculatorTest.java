package com.carwatch.application.schedule;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NextRunCalculatorTest {

    @Test
    void calculateNextReturnsNextDayAtSixForMorningCronInBratislava() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-01-10T11:00:00Z"), ZoneOffset.UTC);
        NextRunCalculator calculator = new NextRunCalculator(fixedClock);

        LocalDateTime next = calculator.calculateNext("0 0 6 * * *", "Europe/Bratislava");

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 1, 11, 6, 0));
    }

    @Test
    void calculateNextReturnsValidLocalDateTime() {
        NextRunCalculator calculator = new NextRunCalculator();

        LocalDateTime next = calculator.calculateNext("0 */15 * * * *", "Europe/Bratislava");

        assertThat(next).isNotNull();
    }
}
