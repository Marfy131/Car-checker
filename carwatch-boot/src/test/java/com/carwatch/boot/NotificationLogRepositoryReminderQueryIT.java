package com.carwatch.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.carwatch.domain.notification.NotificationChannel;
import com.carwatch.domain.notification.NotificationLog;
import com.carwatch.domain.notification.NotificationLogRepository;
import com.carwatch.domain.notification.NotificationStatus;
import com.carwatch.domain.notification.NotificationType;
import com.carwatch.domain.obligation.ObligationType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationLogRepositoryReminderQueryIT {

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Test
    void findReminderLogsByDateReturnsOnlyReminderTypesForTargetDate() {
        LocalDate targetDate = LocalDate.of(2099, 1, 1).plusDays(Math.floorMod(UUID.randomUUID().hashCode(), 10_000));
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        notificationLogRepository.save(notificationLog(
                targetDate,
                NotificationType.EXPIRY_WARNING,
                "expiry-warning-" + uniqueSuffix
        ));
        notificationLogRepository.save(notificationLog(
                targetDate,
                NotificationType.EXPIRED,
                "expired-" + uniqueSuffix
        ));
        notificationLogRepository.save(notificationLog(
                targetDate,
                NotificationType.DAILY_SUMMARY,
                "daily-summary-" + uniqueSuffix
        ));
        notificationLogRepository.save(notificationLog(
                targetDate.plusDays(1),
                NotificationType.EXPIRY_WARNING,
                "other-date-" + uniqueSuffix
        ));

        List<NotificationLog> reminderLogs = notificationLogRepository.findReminderLogsByDate(targetDate);

        assertThat(reminderLogs).hasSize(2);
        assertThat(reminderLogs)
                .extracting(NotificationLog::getNotificationType)
                .containsExactlyInAnyOrder(NotificationType.EXPIRY_WARNING, NotificationType.EXPIRED);
        assertThat(reminderLogs)
                .extracting(NotificationLog::getDedupeDate)
                .containsOnly(targetDate.toString());
    }

    private NotificationLog notificationLog(LocalDate dedupeDate, NotificationType type, String subject) {
        NotificationLog log = new NotificationLog();
        log.setCarId(null);
        log.setInsurancePolicyId(null);
        log.setObligationType(ObligationType.STK);
        log.setNotificationType(type);
        log.setChannel(NotificationChannel.EMAIL);
        log.setSubject(subject);
        log.setRecipient(subject + "@example.com");
        log.setSentAt(LocalDateTime.of(dedupeDate, LocalDateTime.of(2026, 4, 13, 9, 0).toLocalTime()));
        log.setStatus(NotificationStatus.SENT);
        log.setProviderMessageId("msg-" + subject);
        log.setCheckRunId(null);
        log.setDedupeDate(dedupeDate.toString());
        return log;
    }
}
