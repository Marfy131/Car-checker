package com.carwatch.domain.insurance;

import com.carwatch.domain.schedule.CheckOutcome;

public interface PolicyCheckProvider {

    PolicyType supportedPolicyType();

    CheckMode supportedMode();

    CheckOutcome execute(PolicyCheckCommand command);
}
