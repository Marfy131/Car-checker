package com.carwatch.application.car;

import java.time.LocalDate;

public record UpdateCarCommand(
        Long id,
        String name,
        String licensePlate,
        LocalDate registrationDate,
        String vin,
        int version
) {
}
