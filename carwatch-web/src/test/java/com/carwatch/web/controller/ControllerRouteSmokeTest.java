package com.carwatch.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.carwatch.application.schedule.ScheduleManagementService;
import com.carwatch.domain.notification.NotificationLogRepository;
import com.carwatch.domain.schedule.CheckRunLogRepository;
import com.carwatch.domain.setting.AppSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

class ControllerRouteSmokeTest {

    private DashboardController dashboardController;
    private HistoryController historyController;
    private NotificationController notificationController;
    private SettingsController settingsController;
    private ScheduleController scheduleController;

    @BeforeEach
    void setUp() {
        CheckRunLogRepository runLogRepository = org.mockito.Mockito.mock(CheckRunLogRepository.class);
        NotificationLogRepository notificationLogRepository = org.mockito.Mockito.mock(NotificationLogRepository.class);
        AppSettingRepository appSettingRepository = org.mockito.Mockito.mock(AppSettingRepository.class);
        ScheduleManagementService scheduleManagementService = org.mockito.Mockito.mock(ScheduleManagementService.class);

        when(runLogRepository.findRecentRuns(100)).thenReturn(java.util.List.of());
        when(notificationLogRepository.findRecent(100)).thenReturn(java.util.List.of());
        when(appSettingRepository.findAll()).thenReturn(java.util.List.of());
        when(scheduleManagementService.findAll()).thenReturn(java.util.List.of());

        dashboardController = new DashboardController();
        historyController = new HistoryController(runLogRepository);
        notificationController = new NotificationController(notificationLogRepository);
        settingsController = new SettingsController(appSettingRepository);
        scheduleController = new ScheduleController(scheduleManagementService);
    }

    @Test
    void dashboardRouteResolves() {
        assertThat(dashboardController.dashboard()).isEqualTo("dashboard");
    }

    @Test
    void historyRouteResolves() {
        Model model = new ExtendedModelMap();
        assertThat(historyController.history(model)).isEqualTo("history/list");
    }

    @Test
    void notificationsRouteResolves() {
        Model model = new ExtendedModelMap();
        assertThat(notificationController.notifications(model)).isEqualTo("notifications/list");
    }

    @Test
    void settingsRouteResolves() {
        Model model = new ExtendedModelMap();
        assertThat(settingsController.settings(model)).isEqualTo("settings/form");
    }

    @Test
    void schedulesRouteResolves() {
        Model model = new ExtendedModelMap();
        assertThat(scheduleController.list(model)).isEqualTo("schedules/list");
    }
}
