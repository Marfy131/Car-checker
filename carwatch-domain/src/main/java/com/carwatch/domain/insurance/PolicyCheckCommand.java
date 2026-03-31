package com.carwatch.domain.insurance;

public record PolicyCheckCommand(Long insurancePolicyId, Long carId, String licensePlate, PolicyType policyType) {
}
