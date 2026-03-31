package com.carwatch.application.schedule;

import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import com.carwatch.domain.schedule.VehicleCheckProvider;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CheckExecutor {

    private final Map<CheckType, VehicleCheckProvider> providers;

    public CheckExecutor(List<VehicleCheckProvider> providerList) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(VehicleCheckProvider::supportedType, Function.identity(), (left, right) -> right));
    }

    public CheckOutcome execute(CheckCommand command) {
        VehicleCheckProvider provider = providers.get(command.checkType());
        if (provider == null) {
            return new CheckOutcome(RunStatus.ERROR, "No provider for " + command.checkType(), null, null);
        }
        return provider.execute(command);
    }
}
