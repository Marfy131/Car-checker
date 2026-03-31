package com.carwatch.domain.schedule;

public record CheckCommand(Long carId, String licensePlate, String vin, CheckType checkType) {
}
