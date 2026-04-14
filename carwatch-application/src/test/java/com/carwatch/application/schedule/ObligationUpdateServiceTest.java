package com.carwatch.application.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.carwatch.domain.obligation.ExpiryStatus;
import com.carwatch.domain.obligation.ObligationState;
import com.carwatch.domain.obligation.ObligationStateRepository;
import com.carwatch.domain.obligation.ObligationType;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ObligationUpdateServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-10T08:15:30Z"), ZoneOffset.UTC);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 1, 10, 8, 15, 30);

    @Mock
    private ObligationStateRepository obligationStateRepository;

    @Test
    void keepsPreviousExpiryWhenOutcomeHasError() {
        ObligationState existing = new ObligationState();
        existing.setCarId(1L);
        existing.setObligationType(ObligationType.STK);
        existing.setExpiryDate(LocalDate.of(2026, 2, 10));
        existing.setStatus(ExpiryStatus.VALID);

        when(obligationStateRepository.findByCarIdAndObligationType(1L, ObligationType.STK))
                .thenReturn(Optional.of(existing));
        when(obligationStateRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckSchedule schedule = new CheckSchedule();
        schedule.setCarId(1L);
        schedule.setCheckType(CheckType.STK_CHECK);
        schedule.setWarningDaysBefore(20);
        CheckOutcome outcome = new CheckOutcome(RunStatus.ERROR, "fail", null, "{}");

        ObligationUpdateService service = new ObligationUpdateService(obligationStateRepository, FIXED_CLOCK);
        service.updateFromOutcome(schedule, outcome);

        ArgumentCaptor<ObligationState> captor = ArgumentCaptor.forClass(ObligationState.class);
        org.mockito.Mockito.verify(obligationStateRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiryDate()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(captor.getValue().getStatus()).isEqualTo(ExpiryStatus.ERROR);
        assertThat(captor.getValue().getLastCheckedAt()).isEqualTo(FIXED_NOW);
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void resolvesExpiryStatusFromInjectedClock() {
        when(obligationStateRepository.findByCarIdAndObligationType(1L, ObligationType.STK))
            .thenReturn(Optional.empty());
        when(obligationStateRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckSchedule schedule = new CheckSchedule();
        schedule.setCarId(1L);
        schedule.setCheckType(CheckType.STK_CHECK);
        schedule.setWarningDaysBefore(20);

        CheckOutcome outcome = new CheckOutcome(
            RunStatus.SUCCESS,
            "ok",
            LocalDate.of(2026, 1, 30),
            "{\"source\":\"test\"}"
        );

        ObligationUpdateService service = new ObligationUpdateService(obligationStateRepository, FIXED_CLOCK);
        service.updateFromOutcome(schedule, outcome);

        ArgumentCaptor<ObligationState> captor = ArgumentCaptor.forClass(ObligationState.class);
        org.mockito.Mockito.verify(obligationStateRepository).save(captor.capture());
        ObligationState saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ExpiryStatus.EXPIRING);
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getLastCheckedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getLastSuccessfulCheckAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void marksExpiryOnCurrentDateAsExpiring() {
        when(obligationStateRepository.findByCarIdAndObligationType(1L, ObligationType.STK))
            .thenReturn(Optional.empty());
        when(obligationStateRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckOutcome outcome = new CheckOutcome(
            RunStatus.SUCCESS,
            "ok",
            LocalDate.of(2026, 1, 10),
            "{\"source\":\"test\"}"
        );

        ObligationUpdateService service = new ObligationUpdateService(obligationStateRepository, FIXED_CLOCK);
        service.updateFromOutcome(scheduleWithWarningDays(0), outcome);

        ObligationState saved = captureSavedState();
        assertThat(saved.getExpiryDate()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(saved.getStatus()).isEqualTo(ExpiryStatus.EXPIRING);
        assertThat(saved.getLastSuccessfulCheckAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void marksExpiryOutsideWarningWindowAsValid() {
        when(obligationStateRepository.findByCarIdAndObligationType(1L, ObligationType.STK))
            .thenReturn(Optional.empty());
        when(obligationStateRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckOutcome outcome = new CheckOutcome(
            RunStatus.SUCCESS,
            "ok",
            LocalDate.of(2026, 1, 31),
            "{\"source\":\"test\"}"
        );

        ObligationUpdateService service = new ObligationUpdateService(obligationStateRepository, FIXED_CLOCK);
        service.updateFromOutcome(scheduleWithWarningDays(20), outcome);

        ObligationState saved = captureSavedState();
        assertThat(saved.getExpiryDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(saved.getStatus()).isEqualTo(ExpiryStatus.VALID);
        assertThat(saved.getLastSuccessfulCheckAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void keepsPreviousExpiryAndMarksUnknownWhenNonSuccessfulOutcomeHasNoExpiryDate() {
        ObligationState existing = existingState(LocalDate.of(2026, 2, 10), ExpiryStatus.VALID);
        LocalDateTime previousSuccess = LocalDateTime.of(2025, 12, 20, 9, 0);
        existing.setLastSuccessfulCheckAt(previousSuccess);

        when(obligationStateRepository.findByCarIdAndObligationType(1L, ObligationType.STK))
            .thenReturn(Optional.of(existing));
        when(obligationStateRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckOutcome outcome = new CheckOutcome(RunStatus.NO_DATA, "missing", null, "{}");

        ObligationUpdateService service = new ObligationUpdateService(obligationStateRepository, FIXED_CLOCK);
        service.updateFromOutcome(scheduleWithWarningDays(20), outcome);

        ObligationState saved = captureSavedState();
        assertThat(saved.getExpiryDate()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(saved.getStatus()).isEqualTo(ExpiryStatus.UNKNOWN);
        assertThat(saved.getLastSuccessfulCheckAt()).isEqualTo(previousSuccess);
        assertThat(saved.getLastCheckedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void doesNotRefreshLastSuccessfulTimestampForWarningOutcomeWithExpiryDate() {
        ObligationState existing = existingState(LocalDate.of(2026, 2, 10), ExpiryStatus.VALID);
        LocalDateTime previousSuccess = LocalDateTime.of(2025, 12, 20, 9, 0);
        existing.setLastSuccessfulCheckAt(previousSuccess);

        when(obligationStateRepository.findByCarIdAndObligationType(1L, ObligationType.STK))
            .thenReturn(Optional.of(existing));
        when(obligationStateRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckOutcome outcome = new CheckOutcome(
            RunStatus.WARNING,
            "warning",
            LocalDate.of(2026, 1, 15),
            "{\"source\":\"test\"}"
        );

        ObligationUpdateService service = new ObligationUpdateService(obligationStateRepository, FIXED_CLOCK);
        service.updateFromOutcome(scheduleWithWarningDays(20), outcome);

        ObligationState saved = captureSavedState();
        assertThat(saved.getExpiryDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(saved.getLastSuccessfulCheckAt()).isEqualTo(previousSuccess);
        assertThat(saved.getLastCheckedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    private ObligationState captureSavedState() {
        ArgumentCaptor<ObligationState> captor = ArgumentCaptor.forClass(ObligationState.class);
        org.mockito.Mockito.verify(obligationStateRepository).save(captor.capture());
        return captor.getValue();
    }

    private CheckSchedule scheduleWithWarningDays(int warningDaysBefore) {
        CheckSchedule schedule = new CheckSchedule();
        schedule.setCarId(1L);
        schedule.setCheckType(CheckType.STK_CHECK);
        schedule.setWarningDaysBefore(warningDaysBefore);
        return schedule;
    }

    private ObligationState existingState(LocalDate expiryDate, ExpiryStatus status) {
        ObligationState existing = new ObligationState();
        existing.setCarId(1L);
        existing.setObligationType(ObligationType.STK);
        existing.setExpiryDate(expiryDate);
        existing.setStatus(status);
        return existing;
    }
}
