package com.carwatch.application.schedule;

import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import com.carwatch.domain.schedule.VehicleCheckProvider;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckExecutorTest {

    @Test
    void executeDelegatesToMatchingProvider() {
        CheckOutcome expected = new CheckOutcome(RunStatus.SUCCESS, "ok", LocalDate.of(2026, 1, 20), "{\"source\":\"stub\"}");
        VehicleCheckProvider provider = new TestProvider(CheckType.PZP_CHECK, expected);
        CheckExecutor executor = new CheckExecutor(List.of(provider));
        CheckCommand command = new CheckCommand(10L, "BA123AA", "VIN123", CheckType.PZP_CHECK);

        CheckOutcome result = executor.execute(command);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void executeReturnsErrorWhenNoProviderIsRegistered() {
        CheckExecutor executor = new CheckExecutor(List.of());
        CheckCommand command = new CheckCommand(10L, "BA123AA", "VIN123", CheckType.EK_CHECK);

        CheckOutcome result = executor.execute(command);

        assertThat(result.status()).isEqualTo(RunStatus.ERROR);
        assertThat(result.message()).isEqualTo("No provider for EK_CHECK");
        assertThat(result.expiryDateFound()).isNull();
        assertThat(result.findingsJson()).isNull();
    }

    @Test
    void constructorFailsFastWhenDuplicateProvidersAreRegisteredForSameType() {
        VehicleCheckProvider first = new TestProvider(CheckType.PZP_CHECK, new CheckOutcome(RunStatus.SUCCESS, "first", null, null));
        VehicleCheckProvider second = new AlternateTestProvider(CheckType.PZP_CHECK, new CheckOutcome(RunStatus.SUCCESS, "second", null, null));

        assertThatThrownBy(() -> new CheckExecutor(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate provider registration for checkType=PZP_CHECK")
                .hasMessageContaining(TestProvider.class.getName())
                .hasMessageContaining(AlternateTestProvider.class.getName());
    }

    private record TestProvider(CheckType supportedType, CheckOutcome outcome) implements VehicleCheckProvider {
        @Override
        public CheckOutcome execute(CheckCommand command) {
            return outcome;
        }
    }

    private record AlternateTestProvider(CheckType supportedType, CheckOutcome outcome) implements VehicleCheckProvider {
        @Override
        public CheckOutcome execute(CheckCommand command) {
            return outcome;
        }
    }
}
