package com.carwatch.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.carwatch.domain.vignette.CarVignetteSelection;
import com.carwatch.domain.vignette.CountryCode;
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

    private static final int DAILY_SUMMARY_RECENT_RUN_LIMIT = 7;

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
                clock,
                DAILY_SUMMARY_RECENT_RUN_LIMIT
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
    void sendImmediateReminderUsesExpiredTypeForDedupeAndStoredLog() {
        ObligationState state = expiredState(1L, ObligationType.STK);
        Car car = new Car();
        car.setId(1L);
        car.setName("Family car");
        car.setLicensePlate("BA123AA");

        when(notificationLogRepository.existsByCarIdAndObligationTypeAndTypeAndDate(
                1L, ObligationType.STK, NotificationType.EXPIRED, LocalDate.of(2026, 1, 10)))
                .thenReturn(false);
        when(carRepository.findById(1L)).thenReturn(Optional.of(car));
        when(emailSender.send(any())).thenReturn(SendResult.ok("msg-expired"));
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int sent = service.sendImmediateReminderForState(state, "john@example.com");

        assertThat(sent).isEqualTo(1);
        verify(notificationLogRepository).existsByCarIdAndObligationTypeAndTypeAndDate(
                1L, ObligationType.STK, NotificationType.EXPIRED, LocalDate.of(2026, 1, 10));
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.EXPIRED);
    }

    @Test
    void sendDailySummaryUsesConfiguredRecentRunLimit() {
        when(carRepository.findAll()).thenReturn(List.of());
        when(obligationStateRepository.findAll()).thenReturn(List.of());
        when(carVignetteSelectionRepository.findAll()).thenReturn(List.of());
        when(checkRunLogRepository.findRecentRuns(DAILY_SUMMARY_RECENT_RUN_LIMIT)).thenReturn(List.of());
        when(dailySummaryTemplateRenderer.render(any(), any(Locale.class))).thenReturn("<html>summary</html>");
        when(emailSender.send(any())).thenReturn(SendResult.ok("x"));
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int sent = service.sendDailySummary("a@example.com, b@example.com", Locale.ENGLISH);

        assertThat(sent).isEqualTo(2);
        verify(checkRunLogRepository).findRecentRuns(DAILY_SUMMARY_RECENT_RUN_LIMIT);
    }

    @Test
    void sendDailySummaryBuildsStructuredCarItemsAndOnlyIncludesEnabledVignettes() {
        Car car = new Car();
        car.setId(1L);
        car.setName("Family car");
        car.setLicensePlate("BA123AA");

        ObligationState pzp = new ObligationState();
        pzp.setCarId(1L);
        pzp.setObligationType(ObligationType.PZP);
        pzp.setStatus(ExpiryStatus.VALID);
        pzp.setExpiryDate(LocalDate.of(2026, 2, 1));

        ObligationState stk = new ObligationState();
        stk.setCarId(1L);
        stk.setObligationType(ObligationType.STK);
        stk.setStatus(ExpiryStatus.EXPIRING);
        stk.setExpiryDate(LocalDate.of(2026, 1, 12));

        ObligationState vignetteSk = new ObligationState();
        vignetteSk.setCarId(1L);
        vignetteSk.setObligationType(ObligationType.VIGNETTE_SK);
        vignetteSk.setStatus(ExpiryStatus.EXPIRED);
        vignetteSk.setExpiryDate(LocalDate.of(2026, 1, 9));

        when(carRepository.findAll()).thenReturn(List.of(car));
        when(obligationStateRepository.findAll()).thenReturn(List.of(pzp, stk, vignetteSk));
        when(carVignetteSelectionRepository.findAll()).thenReturn(List.of(
                vignetteSelection(1L, CountryCode.SK, true),
                vignetteSelection(1L, CountryCode.CZ, false)));
        when(checkRunLogRepository.findRecentRuns(DAILY_SUMMARY_RECENT_RUN_LIMIT)).thenReturn(List.of());
        when(dailySummaryTemplateRenderer.render(any(), any(Locale.class))).thenReturn("<html>summary</html>");
        when(emailSender.send(any())).thenReturn(SendResult.ok("x"));
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int sent = service.sendDailySummary("a@example.com", Locale.ENGLISH);

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<DailySummaryModel> captor = ArgumentCaptor.forClass(DailySummaryModel.class);
        verify(dailySummaryTemplateRenderer).render(captor.capture(), any(Locale.class));
        DailySummaryModel summaryModel = captor.getValue();
        assertThat(summaryModel.cars()).hasSize(1);
        DailySummaryModel.CarSummary carSummary = summaryModel.cars().getFirst();
        assertThat(carSummary.name()).isEqualTo("Family car");
        assertThat(carSummary.licensePlate()).isEqualTo("BA123AA");
        assertThat(carSummary.items())
                .extracting(DailySummaryModel.SummaryItem::label)
                .containsExactly("PZP", "Collision insurance", "STK", "EK", "Vignette SK");
        assertThat(carSummary.items())
                .extracting(DailySummaryModel.SummaryItem::expiry)
                .containsExactly("2026-02-01", "-", "2026-01-12", "-", "2026-01-09");
        assertThat(carSummary.items())
                .extracting(DailySummaryModel.SummaryItem::status)
                .containsExactly("VALID", "UNKNOWN", "EXPIRING", "UNKNOWN", "EXPIRED");
        assertThat(summaryModel.warnings()).containsExactly(
                "BA123AA: STK (EXPIRING)",
                "BA123AA: Vignette SK (EXPIRED)");
    }

    @Test
    void sendDailyRemindersPreloadsCarsAndNotificationLogsOnce() {
        ObligationState expiring = expiringState();
        ObligationState expired = expiredState(2L, ObligationType.EK);

        Car firstCar = new Car();
        firstCar.setId(1L);
        firstCar.setName("Family car");
        firstCar.setLicensePlate("BA123AA");

        Car secondCar = new Car();
        secondCar.setId(2L);
        secondCar.setName("Work car");
        secondCar.setLicensePlate("TT456BB");

        when(obligationStateRepository.findAll()).thenReturn(List.of(expiring, expired));
        when(carRepository.findAll()).thenReturn(List.of(firstCar, secondCar));
        when(notificationLogRepository.findReminderLogsByDate(LocalDate.of(2026, 1, 10))).thenReturn(List.of(existingLog(
                1L,
                ObligationType.STK,
                NotificationType.EXPIRY_WARNING,
                "2026-01-10")));
        when(emailSender.send(any())).thenReturn(SendResult.ok("msg-2"));
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int sent = service.sendDailyReminders("john@example.com");

        assertThat(sent).isEqualTo(1);
        verify(carRepository).findAll();
        verify(notificationLogRepository).findReminderLogsByDate(LocalDate.of(2026, 1, 10));
        verify(notificationLogRepository, never()).findAll();
        verify(carRepository, never()).findById(any());
        verify(notificationLogRepository, never()).existsByCarIdAndObligationTypeAndTypeAndDate(any(), any(), any(), any());
        verify(emailSender, times(1)).send(any());
        verify(notificationLogRepository, times(1)).save(any(NotificationLog.class));
    }

    @Test
    void sendDailyRemindersDoesNotTreatExpiryWarningLogAsDuplicateForExpiredReminder() {
        ObligationState state = expiredState(1L, ObligationType.STK);

        Car car = new Car();
        car.setId(1L);
        car.setName("Family car");
        car.setLicensePlate("BA123AA");

        when(obligationStateRepository.findAll()).thenReturn(List.of(state));
        when(carRepository.findAll()).thenReturn(List.of(car));
        when(notificationLogRepository.findReminderLogsByDate(LocalDate.of(2026, 1, 10))).thenReturn(List.of(existingLog(
                1L,
                ObligationType.STK,
                NotificationType.EXPIRY_WARNING,
                "2026-01-10")));
        when(emailSender.send(any())).thenReturn(SendResult.ok("msg-expired-daily"));
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int sent = service.sendDailyReminders("john@example.com");

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.EXPIRED);
    }

    @Test
    void sendDailyRemindersDedupesRepeatedStateWithinSingleRun() {
        ObligationState first = expiringState();
        ObligationState duplicate = expiringState();

        Car car = new Car();
        car.setId(1L);
        car.setName("Family car");
        car.setLicensePlate("BA123AA");

        when(obligationStateRepository.findAll()).thenReturn(List.of(first, duplicate));
        when(carRepository.findAll()).thenReturn(List.of(car));
        when(notificationLogRepository.findReminderLogsByDate(LocalDate.of(2026, 1, 10))).thenReturn(List.of());
        when(emailSender.send(any())).thenReturn(SendResult.ok("msg-3"));
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int sent = service.sendDailyReminders("john@example.com");

        assertThat(sent).isEqualTo(1);
        verify(emailSender, times(1)).send(any());
        verify(notificationLogRepository, times(1)).save(any(NotificationLog.class));
    }

    private ObligationState expiringState() {
        ObligationState state = new ObligationState();
        state.setCarId(1L);
        state.setObligationType(ObligationType.STK);
        state.setStatus(ExpiryStatus.EXPIRING);
        state.setExpiryDate(LocalDate.of(2026, 1, 15));
        return state;
    }

    private ObligationState expiredState(Long carId, ObligationType obligationType) {
        ObligationState state = new ObligationState();
        state.setCarId(carId);
        state.setObligationType(obligationType);
        state.setStatus(ExpiryStatus.EXPIRED);
        state.setExpiryDate(LocalDate.of(2026, 1, 9));
        return state;
    }

    private NotificationLog existingLog(
            Long carId,
            ObligationType obligationType,
            NotificationType notificationType,
            String dedupeDate) {
        NotificationLog log = new NotificationLog();
        log.setCarId(carId);
        log.setObligationType(obligationType);
        log.setNotificationType(notificationType);
        log.setDedupeDate(dedupeDate);
        return log;
    }

    private CarVignetteSelection vignetteSelection(Long carId, CountryCode country, boolean enabled) {
        CarVignetteSelection selection = new CarVignetteSelection();
        selection.setCarId(carId);
        selection.setCountry(country);
        selection.setEnabled(enabled);
        return selection;
    }
}
