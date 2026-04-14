package com.carwatch.application.notification;

import com.carwatch.domain.notification.NotificationLog;
import com.carwatch.domain.notification.NotificationLogRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationQueryService {

    private final NotificationLogRepository notificationLogRepository;
    private final int recentNotificationLimit;

    @Autowired
    public NotificationQueryService(
        NotificationLogRepository notificationLogRepository,
        @Value("${carwatch.notifications.page-size:100}") int recentNotificationLimit
    ) {
        this.notificationLogRepository = notificationLogRepository;
        this.recentNotificationLimit = recentNotificationLimit;
    }

    NotificationQueryService(NotificationLogRepository notificationLogRepository) {
        this(notificationLogRepository, 100);
    }

    public List<NotificationLog> findRecentNotifications() {
        return notificationLogRepository.findRecent(recentNotificationLimit);
    }
}
