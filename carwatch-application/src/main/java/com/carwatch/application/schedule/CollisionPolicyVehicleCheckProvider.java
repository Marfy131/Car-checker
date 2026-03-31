package com.carwatch.application.schedule;

import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.VehicleCheckProvider;
import org.springframework.stereotype.Component;

@Component
public class CollisionPolicyVehicleCheckProvider implements VehicleCheckProvider {

    private final ManualFirstPolicyVehicleCheckProvider delegate;

    public CollisionPolicyVehicleCheckProvider(ManualFirstPolicyVehicleCheckProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public CheckType supportedType() {
        return CheckType.COLLISION_INSURANCE_CHECK;
    }

    @Override
    public CheckOutcome execute(CheckCommand command) {
        return delegate.executeCollision(command);
    }
}
