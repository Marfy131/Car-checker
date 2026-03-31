package com.carwatch.application.car;

import com.carwatch.domain.vignette.CountryCode;
import java.time.LocalDate;
import java.util.Set;

public record CreateCarCommand(
        String name,
        String licensePlate,
        LocalDate registrationDate,
        String vin,
        Set<CountryCode> vignetteCountries
) {
}
