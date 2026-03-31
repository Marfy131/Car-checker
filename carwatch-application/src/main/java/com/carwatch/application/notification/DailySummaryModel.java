package com.carwatch.application.notification;

import java.util.List;

public record DailySummaryModel(
        String generatedAt,
        List<String> warnings,
        List<CarSummaryLine> cars,
        List<String> recentRuns
) {
    public record CarSummaryLine(
            String name,
            String licensePlate,
            String pzpExpiry,
            String pzpStatus,
            String collisionExpiry,
            String collisionStatus,
            String stkExpiry,
            String stkStatus,
            String ekExpiry,
            String ekStatus,
            boolean skVignetteEnabled,
            String skVignetteExpiry,
            String skVignetteStatus,
            boolean czVignetteEnabled,
            String czVignetteExpiry,
            String czVignetteStatus,
            boolean atVignetteEnabled,
            String atVignetteExpiry,
            String atVignetteStatus
    ) {
    }
}
