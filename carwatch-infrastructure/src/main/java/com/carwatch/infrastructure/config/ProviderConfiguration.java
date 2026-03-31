package com.carwatch.infrastructure.config;

import com.carwatch.application.notification.ReminderNotificationService;
import com.carwatch.application.schedule.ObligationStateVehicleCheckProvider;
import com.carwatch.application.schedule.ScheduledWorkflowProvider;
import com.carwatch.domain.obligation.ObligationStateRepository;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.VehicleCheckProvider;
import com.carwatch.domain.setting.AppSettingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderConfiguration {

    @Bean
    VehicleCheckProvider stkCheckProvider(ObligationStateRepository obligationStateRepository) {
        return new ObligationStateVehicleCheckProvider(CheckType.STK_CHECK, obligationStateRepository);
    }

    @Bean
    VehicleCheckProvider ekCheckProvider(ObligationStateRepository obligationStateRepository) {
        return new ObligationStateVehicleCheckProvider(CheckType.EK_CHECK, obligationStateRepository);
    }

    @Bean
    VehicleCheckProvider vignetteSkCheckProvider(ObligationStateRepository obligationStateRepository) {
        return new ObligationStateVehicleCheckProvider(CheckType.VIGNETTE_SK_CHECK, obligationStateRepository);
    }

    @Bean
    VehicleCheckProvider vignetteCzCheckProvider(ObligationStateRepository obligationStateRepository) {
        return new ObligationStateVehicleCheckProvider(CheckType.VIGNETTE_CZ_CHECK, obligationStateRepository);
    }

    @Bean
    VehicleCheckProvider vignetteAtCheckProvider(ObligationStateRepository obligationStateRepository) {
        return new ObligationStateVehicleCheckProvider(CheckType.VIGNETTE_AT_CHECK, obligationStateRepository);
    }

    @Bean
    VehicleCheckProvider dailyReminderScanProvider(
            ReminderNotificationService reminderNotificationService,
            AppSettingRepository appSettingRepository
    ) {
        return new ScheduledWorkflowProvider(
                CheckType.DAILY_REMINDER_SCAN,
                reminderNotificationService,
                appSettingRepository
        );
    }

    @Bean
    VehicleCheckProvider dailySummaryProvider(
            ReminderNotificationService reminderNotificationService,
            AppSettingRepository appSettingRepository
    ) {
        return new ScheduledWorkflowProvider(
                CheckType.DAILY_SUMMARY_EMAIL,
                reminderNotificationService,
                appSettingRepository
        );
    }
}
