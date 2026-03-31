package com.carwatch.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carwatch.domain.car.Car;
import com.carwatch.domain.car.CarRepository;
import com.carwatch.domain.notification.NotificationLog;
import com.carwatch.domain.notification.NotificationLogRepository;
import com.carwatch.domain.notification.NotificationType;
import com.carwatch.domain.obligation.ExpiryStatus;
import com.carwatch.domain.obligation.ObligationState;
import com.carwatch.domain.obligation.ObligationStateRepository;
import com.carwatch.domain.obligation.ObligationType;
import com.carwatch.domain.schedule.CheckRunLogRepository;
import com.carwatch.domain.vignette.CarVignetteSelectionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReminderNotificationServiceTest {

    @Mock
    private ObligationStateRepository obligationStateRepository;
    @Mock
    private NotificationLogRepository notificationLogRepository;
    @Mock
    private CarRepository carRepository;
    @Mock
    private EmailSender emailSender;
    @Mock
    private DailySummaryTemplateRenderer dailySummaryTemplateRenderer;
    @Mock
    private CheckRunLogRepository checkRunLogRepository;
    @Mock
    private CarVignetteSelectionRepository carVignetteSelectionRepository;

    private ReminderNotificationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-10T06:00:00Z"), ZoneOffset.UTC);
        service = new ReminderNotificationService(
                obligationStateRepository,
                notificationLogRepository,
                carRepository,
                emailSender,
                dailySummaryTemplateRenderer,
                checkRunLogRepository,
                carVignetteSelectionRepository,
                clock
        );
    }

    @Test
    void sendImmediateReminderSkipsWhenAlreadySentForSameDayAndType() {
        ObligationState state = expiringState();
        when(notificationLogRepository.existsByCarIdAndObligationTypeAndTypeAndDate(
                1L, ObligationType.STK, NotificationType.EXPIRY_WARNING, LocalDate.of(2026, 1, 10)))
                .thenReturn(true);

        int sent = service.sendImmediateReminderForState(state, "john@example.com");

        assertThat(sent).isZero();
        verify(emailSender, never()).send(any());
    }

    @Test
    void sendImmediateReminderStoresNotificationLogOnSuccess() {
        ObligationState state = expiringState();
        Car car = new Car();
        car.setId(1L);
        car.setName("Family car");
        car.setLicensePlate("BA123AA");

        when(notificationLogRepository.existsByCarIdAndObligationTypeAndTypeAndDate(
                1L, ObligationType.STK, NotificationType.EXPIRY_WARNING, LocalDate.of(2026, 1, 10)))
                .thenReturn(false);
        when(carRepository.findById(1L)).thenReturn(Optional.of(car));
        when(emailSender.send(any())).thenReturn(SendResult.ok("msg-1"));
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int sent = service.sendImmediateReminderForState(state, "john@example.com");

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.EXPIRY_WARNING);
        assertThat(saved.getObligationType()).isEqualTo(ObligationType.STK);
        assertThat(saved.getRecipient()).isEqualTo("john@example.com");
        assertThat(saved.getDedupeDate()).isEqualTo("2026-01-10");
    }

    @Test
    void sendDailySummaryUsesTemplateRendererAndSendsToAllRecipients() {
        when(carRepository.findAll()).thenReturn(List.of());
        when(obligationStateRepository.findAll()).thenReturn(List.of());
        when(carVignetteSelectionRepository.findAll()).thenReturn(List.of());
        when(checkRunLogRepository.findRecentRuns(10)).thenReturn(List.of());
        when(dailySummaryTemplateRenderer.render(any(), any(Locale.class))).thenReturn("<html>summary</html>");
        when(emailSender.send(any())).thenReturn(SendResult.ok("x"));
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int sent = service.sendDailySummary("a@example.com, b@example.com", Locale.ENGLISH);

        assertThat(sent).isEqualTo(2);
    }

    private ObligationState expiringState() {
        ObligationState state = new ObligationState();
        state.setCarId(1L);
        state.setObligationType(ObligationType.STK);
        state.setStatus(ExpiryStatus.EXPIRING);
        state.setExpiryDate(LocalDate.of(2026, 1, 15));
        return state;
    }
}
