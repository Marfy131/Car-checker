package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.notification.NotificationLog;
import com.carwatch.domain.notification.NotificationLogRepository;
import com.carwatch.domain.notification.NotificationType;
import com.carwatch.domain.obligation.ObligationType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotificationLogRepositoryAdapter implements NotificationLogRepository {

    private final JdbcNotificationLogRepository jdbc;

    public NotificationLogRepositoryAdapter(JdbcNotificationLogRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public NotificationLog save(NotificationLog log) {
        return jdbc.save(log);
    }

    @Override
    public List<NotificationLog> findRecent(int limit) {
        return jdbc.findRecent(limit);
    }

    @Override
    public boolean existsByCarIdAndObligationTypeAndTypeAndDate(
            Long carId,
            ObligationType obligationType,
            NotificationType type,
            LocalDate date
    ) {
        return jdbc.existsByCarIdAndObligationTypeAndTypeAndDate(
                carId,
                obligationType == null ? null : obligationType.name(),
                type.name(),
                date.toString()
        );
    }

    @Override
    public List<NotificationLog> findAll() {
        return jdbc.findAll();
    }
}
