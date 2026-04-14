package com.carwatch.application.notification;

import java.util.List;

public record DailySummaryModel(
        String generatedAt,
        List<String> warnings,
        List<CarSummary> cars,
        List<String> recentRuns
) {
    public record CarSummary(
            String name,
            String licensePlate,
            List<SummaryItem> items
    ) {
    }

    public record SummaryItem(
            String label,
            String expiry,
            String status
    ) {
    }
}
