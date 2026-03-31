package com.carwatch.domain.schedule;

public interface VehicleCheckProvider {

    CheckType supportedType();

    CheckOutcome execute(CheckCommand command);
}
