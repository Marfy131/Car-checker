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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CheckExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CheckExecutor.class);

    private final Map<CheckType, VehicleCheckProvider> providers;

    public CheckExecutor(List<VehicleCheckProvider> providerList) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(
                    VehicleCheckProvider::supportedType,
                    Function.identity(),
                    CheckExecutor::rejectDuplicateProvider
            ));
    }

    public CheckOutcome execute(CheckCommand command) {
        VehicleCheckProvider provider = providers.get(command.checkType());
        if (provider == null) {
            logger.warn(
                    "No provider registered for check execution carId={} checkType={}",
                    command.carId(),
                    command.checkType()
            );
            return new CheckOutcome(RunStatus.ERROR, "No provider for " + command.checkType(), null, null);
        }
        logger.debug(
                "Executing check provider carId={} checkType={} provider={}",
                command.carId(),
                command.checkType(),
                provider.getClass().getSimpleName()
        );
        return provider.execute(command);
    }

    private static VehicleCheckProvider rejectDuplicateProvider(VehicleCheckProvider left, VehicleCheckProvider right) {
        throw new IllegalStateException(
                "Duplicate provider registration for checkType=%s: %s and %s".formatted(
                        left.supportedType(),
                        left.getClass().getName(),
                        right.getClass().getName()
                )
        );
    }
}
