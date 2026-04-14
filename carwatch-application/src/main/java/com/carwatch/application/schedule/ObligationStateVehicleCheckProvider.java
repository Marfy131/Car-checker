package com.carwatch.application.schedule;

import com.carwatch.domain.obligation.ObligationState;
import com.carwatch.domain.obligation.ObligationStateRepository;
import com.carwatch.domain.obligation.ObligationType;
import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.CheckTypeMapping;
import com.carwatch.domain.schedule.RunStatus;
import com.carwatch.domain.schedule.VehicleCheckProvider;
 
public class ObligationStateVehicleCheckProvider implements VehicleCheckProvider {

    private final CheckType checkType;
    private final ObligationStateRepository obligationStateRepository;

    public ObligationStateVehicleCheckProvider(CheckType checkType, ObligationStateRepository obligationStateRepository) {
        this.checkType = checkType;
        this.obligationStateRepository = obligationStateRepository;
    }

    @Override
    public CheckType supportedType() {
        return checkType;
    }

    @Override
    public CheckOutcome execute(CheckCommand command) {
        ObligationType obligationType = CheckTypeMapping.findObligationType(command.checkType()).orElse(null);
        if (command.carId() == null || obligationType == null) {
            return new CheckOutcome(RunStatus.NO_DATA, "Missing target car for check", null, null);
        }

        ObligationState state = obligationStateRepository.findByCarIdAndObligationType(command.carId(), obligationType).orElse(null);
        if (state == null) {
            return new CheckOutcome(RunStatus.NO_DATA, "No obligation state found", null, null);
        }

        return new CheckOutcome(
                RunStatus.SUCCESS,
                "Manual-first provider reused existing obligation state",
                state.getExpiryDate(),
                "{\"source\":\"obligation-state\"}"
        );
    }
}
