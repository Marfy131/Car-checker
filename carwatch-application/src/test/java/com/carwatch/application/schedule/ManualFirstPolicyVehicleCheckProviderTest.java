package com.carwatch.application.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.carwatch.domain.insurance.CheckMode;
import com.carwatch.domain.insurance.InsurancePolicy;
import com.carwatch.domain.insurance.InsurancePolicyRepository;
import com.carwatch.domain.insurance.PolicyCheckProvider;
import com.carwatch.domain.insurance.PolicyType;
import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManualFirstPolicyVehicleCheckProviderTest {

    @Mock
    private InsurancePolicyRepository insurancePolicyRepository;
    @Mock
    private PolicyCheckProvider onlineProvider;

    @Test
    void returnsStoredExpiryForManualPolicy() {
        InsurancePolicy policy = new InsurancePolicy();
        policy.setCarId(1L);
        policy.setPolicyType(PolicyType.PZP);
        policy.setCheckMode(CheckMode.MANUAL);
        policy.setExpiryDate(LocalDate.of(2026, 2, 1));
        when(insurancePolicyRepository.findByCarId(1L)).thenReturn(List.of(policy));

        ManualFirstPolicyVehicleCheckProvider provider =
                new ManualFirstPolicyVehicleCheckProvider(insurancePolicyRepository, List.of());

        CheckOutcome result = provider.execute(new CheckCommand(1L, "BA123AA", "VIN1", CheckType.PZP_CHECK));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.expiryDateFound()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void keepsLastKnownExpiryWhenOnlineProviderFails() {
        InsurancePolicy policy = new InsurancePolicy();
        policy.setId(10L);
        policy.setCarId(1L);
        policy.setPolicyType(PolicyType.PZP);
        policy.setCheckMode(CheckMode.ONLINE);
        policy.setExpiryDate(LocalDate.of(2026, 2, 1));

        when(insurancePolicyRepository.findByCarId(1L)).thenReturn(List.of(policy));
        when(onlineProvider.supportedPolicyType()).thenReturn(PolicyType.PZP);
        when(onlineProvider.supportedMode()).thenReturn(CheckMode.ONLINE);
        when(onlineProvider.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new CheckOutcome(RunStatus.ERROR, "provider fail", null, "{}"));

        ManualFirstPolicyVehicleCheckProvider provider =
                new ManualFirstPolicyVehicleCheckProvider(insurancePolicyRepository, List.of(onlineProvider));

        CheckOutcome result = provider.execute(new CheckCommand(1L, "BA123AA", "VIN1", CheckType.PZP_CHECK));

        assertThat(result.status()).isEqualTo(RunStatus.ERROR);
        assertThat(result.expiryDateFound()).isEqualTo(LocalDate.of(2026, 2, 1));
    }
}
