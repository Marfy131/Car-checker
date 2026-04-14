package com.carwatch.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.carwatch.domain.notification.NotificationLog;
import com.carwatch.domain.notification.NotificationLogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationQueryServiceTest {

    @Test
    void findRecentNotificationsUsesConfiguredPageSize() {
        NotificationLogRepository notificationLogRepository = mock(NotificationLogRepository.class);
        int configuredPageSize = 37;
        List<NotificationLog> notifications = List.of(mock(NotificationLog.class));
        when(notificationLogRepository.findRecent(configuredPageSize)).thenReturn(notifications);

        NotificationQueryService notificationQueryService =
                new NotificationQueryService(notificationLogRepository, configuredPageSize);

        assertThat(notificationQueryService.findRecentNotifications()).isSameAs(notifications);
        verify(notificationLogRepository).findRecent(configuredPageSize);
        verifyNoMoreInteractions(notificationLogRepository);
    }
}
