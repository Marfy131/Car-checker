package com.carwatch.application.schedule;

import com.carwatch.domain.insurance.CheckMode;
import com.carwatch.domain.insurance.InsurancePolicy;
import com.carwatch.domain.insurance.InsurancePolicyRepository;
import com.carwatch.domain.insurance.PolicyCheckCommand;
import com.carwatch.domain.insurance.PolicyCheckProvider;
import com.carwatch.domain.insurance.PolicyType;
import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import com.carwatch.domain.schedule.VehicleCheckProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ManualFirstPolicyVehicleCheckProvider implements VehicleCheckProvider {

    private final InsurancePolicyRepository insurancePolicyRepository;
    private final List<PolicyCheckProvider> policyCheckProviders;

    public ManualFirstPolicyVehicleCheckProvider(
            InsurancePolicyRepository insurancePolicyRepository,
            List<PolicyCheckProvider> policyCheckProviders
    ) {
        this.insurancePolicyRepository = insurancePolicyRepository;
        this.policyCheckProviders = policyCheckProviders;
    }

    @Override
    public CheckType supportedType() {
        return CheckType.PZP_CHECK;
    }

    @Override
    public CheckOutcome execute(CheckCommand command) {
        return executeByType(command, PolicyType.PZP);
    }

    public CheckOutcome executeCollision(CheckCommand command) {
        return executeByType(command, PolicyType.COLLISION);
    }

    private CheckOutcome executeByType(CheckCommand command, PolicyType policyType) {
        if (command.carId() == null) {
            return new CheckOutcome(RunStatus.NO_DATA, "Missing car id for policy check", null, null);
        }

        InsurancePolicy policy = insurancePolicyRepository.findByCarId(command.carId()).stream()
                .filter(candidate -> candidate.getPolicyType() == policyType)
                .findFirst()
                .orElse(null);
        if (policy == null) {
            return new CheckOutcome(RunStatus.NO_DATA, "Policy not found for car " + command.carId(), null, null);
        }

        if (policy.getCheckMode() == null || policy.getCheckMode() == CheckMode.MANUAL) {
            return new CheckOutcome(
                    RunStatus.SUCCESS,
                    "Manual policy mode - reusing stored expiry date",
                    policy.getExpiryDate(),
                    "{\"mode\":\"MANUAL\"}"
            );
        }

        PolicyCheckProvider provider = policyCheckProviders.stream()
                .filter(candidate -> candidate.supportedPolicyType() == policyType && candidate.supportedMode() == CheckMode.ONLINE)
                .findFirst()
                .orElse(null);

        if (provider == null) {
            return new CheckOutcome(
                    RunStatus.WARNING,
                    "Online policy provider not configured, keeping stored expiry date",
                    policy.getExpiryDate(),
                    "{\"mode\":\"ONLINE\",\"provider\":\"missing\"}"
            );
        }

        CheckOutcome result = provider.execute(new PolicyCheckCommand(
                policy.getId(),
                policy.getCarId(),
                command.licensePlate(),
                policyType
        ));

        if (result.status() == RunStatus.ERROR || result.expiryDateFound() == null) {
            return new CheckOutcome(
                    result.status(),
                    result.message(),
                    policy.getExpiryDate(),
                    result.findingsJson()
            );
        }
        return result;
    }
}
