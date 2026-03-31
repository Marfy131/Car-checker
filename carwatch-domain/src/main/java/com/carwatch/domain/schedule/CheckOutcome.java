package com.carwatch.domain.schedule;

import java.time.LocalDate;

public record CheckOutcome(RunStatus status, String message, LocalDate expiryDateFound, String findingsJson) {
}
