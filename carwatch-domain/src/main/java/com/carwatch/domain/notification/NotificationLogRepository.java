package com.carwatch.domain.notification;

import java.time.LocalDate;
import com.carwatch.domain.obligation.ObligationType;
import java.util.List;

public interface NotificationLogRepository {

    NotificationLog save(NotificationLog log);

    List<NotificationLog> findRecent(int limit);

    List<NotificationLog> findReminderLogsByDate(LocalDate date);

    boolean existsByCarIdAndObligationTypeAndTypeAndDate(
            Long carId,
            ObligationType obligationType,
            NotificationType type,
            LocalDate date
    );

    List<NotificationLog> findAll();
}
