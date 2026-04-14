package com.carwatch.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carwatch.application.history.HistoryQueryService;
import com.carwatch.application.notification.NotificationQueryService;
import com.carwatch.application.settings.SettingsService;
import com.carwatch.application.schedule.ScheduleManagementService;
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
    private HistoryQueryService historyQueryService;
    private NotificationQueryService notificationQueryService;

    @BeforeEach
    void setUp() {
        historyQueryService = org.mockito.Mockito.mock(HistoryQueryService.class);
        notificationQueryService = org.mockito.Mockito.mock(NotificationQueryService.class);
        SettingsService settingsService = org.mockito.Mockito.mock(SettingsService.class);
        ScheduleManagementService scheduleManagementService = org.mockito.Mockito.mock(ScheduleManagementService.class);

        when(historyQueryService.findRecentRuns()).thenReturn(java.util.List.of());
        when(notificationQueryService.findRecentNotifications()).thenReturn(java.util.List.of());
        when(settingsService.loadSettings()).thenReturn(new SettingsService.SettingsSnapshot("sk", "", java.util.List.of()));
        when(scheduleManagementService.findAll()).thenReturn(java.util.List.of());

        dashboardController = new DashboardController();
        historyController = new HistoryController(historyQueryService);
        notificationController = new NotificationController(notificationQueryService);
        settingsController = new SettingsController(settingsService);
        scheduleController = new ScheduleController(scheduleManagementService);
    }

    @Test
    void dashboardRouteResolves() {
        assertThat(dashboardController.dashboard()).isEqualTo("dashboard");
    }

    @Test
    void historyRouteResolves() {
        java.util.List<com.carwatch.domain.schedule.CheckRunLog> runs =
                java.util.List.of(org.mockito.Mockito.mock(com.carwatch.domain.schedule.CheckRunLog.class));
        when(historyQueryService.findRecentRuns()).thenReturn(runs);

        Model model = new ExtendedModelMap();
        assertThat(historyController.history(model)).isEqualTo("history/list");
        assertThat(model.asMap()).containsEntry("runs", runs);
        verify(historyQueryService).findRecentRuns();
    }

    @Test
    void notificationsRouteResolves() {
        java.util.List<com.carwatch.domain.notification.NotificationLog> notifications =
                java.util.List.of(org.mockito.Mockito.mock(com.carwatch.domain.notification.NotificationLog.class));
        when(notificationQueryService.findRecentNotifications()).thenReturn(notifications);

        Model model = new ExtendedModelMap();
        assertThat(notificationController.notifications(model)).isEqualTo("notifications/list");
        assertThat(model.asMap()).containsEntry("notifications", notifications);
        verify(notificationQueryService).findRecentNotifications();
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
