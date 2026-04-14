package com.carwatch.application.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import com.carwatch.domain.schedule.CheckType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ScheduleManagementServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-10T08:15:30Z"), ZoneOffset.UTC);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 1, 10, 8, 15, 30);

    @Mock
    private CheckScheduleRepository checkScheduleRepository;
    @Mock
    private ObjectProvider<ScheduleDispatcher> scheduleDispatcherProvider;
    @Mock
    private ScheduleDispatcher scheduleDispatcher;

    @Test
    void updateScheduleUsesInjectedClockForUpdatedAtAndNextRunAt() {
        CheckSchedule schedule = schedule(7L, CheckType.PZP_CHECK);
        when(checkScheduleRepository.findById(7L)).thenReturn(Optional.of(schedule));
        when(checkScheduleRepository.save(org.mockito.ArgumentMatchers.any(CheckSchedule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleManagementService service = new ScheduleManagementService(
            checkScheduleRepository,
            scheduleDispatcherProvider,
            FIXED_CLOCK
        );

        CheckSchedule updated = service.updateSchedule(7L, "0 0 6 * * *", 21, true);

        assertThat(updated.getCronExpression()).isEqualTo("0 0 6 * * *");
        assertThat(updated.getWarningDaysBefore()).isEqualTo(21);
        assertThat(updated.isEnabled()).isTrue();
        assertThat(updated.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(updated.getNextRunAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void runNowUsesInjectedClockAndTriggersDispatcherForDailySchedules() {
        CheckSchedule schedule = schedule(9L, CheckType.DAILY_SUMMARY_EMAIL);
        when(checkScheduleRepository.findById(9L)).thenReturn(Optional.of(schedule));
        when(checkScheduleRepository.save(org.mockito.ArgumentMatchers.any(CheckSchedule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduleDispatcherProvider.getIfAvailable()).thenReturn(scheduleDispatcher);

        ScheduleManagementService service = new ScheduleManagementService(
            checkScheduleRepository,
            scheduleDispatcherProvider,
            FIXED_CLOCK
        );

        service.runNow(9L);

        ArgumentCaptor<CheckSchedule> captor = ArgumentCaptor.forClass(CheckSchedule.class);
        verify(checkScheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getNextRunAt()).isEqualTo(FIXED_NOW.minusSeconds(1));
        verify(scheduleDispatcher).pollAndExecute();
    }

    @Test
    void runNowWaitsForDispatcherCompletionForDailySchedules() throws Exception {
        CheckSchedule schedule = schedule(9L, CheckType.DAILY_SUMMARY_EMAIL);
        when(checkScheduleRepository.findById(9L)).thenReturn(Optional.of(schedule));
        when(checkScheduleRepository.save(org.mockito.ArgumentMatchers.any(CheckSchedule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduleDispatcherProvider.getIfAvailable()).thenReturn(scheduleDispatcher);

        CountDownLatch dispatcherEntered = new CountDownLatch(1);
        CountDownLatch allowDispatcherToFinish = new CountDownLatch(1);
        doAnswer(invocation -> {
            dispatcherEntered.countDown();
            if (!allowDispatcherToFinish.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to unblock dispatcher");
            }
            return null;
        }).when(scheduleDispatcher).pollAndExecute();

        ScheduleManagementService service = new ScheduleManagementService(
            checkScheduleRepository,
            scheduleDispatcherProvider,
            FIXED_CLOCK
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> service.runNow(9L));

            assertThat(dispatcherEntered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(future.isDone()).isFalse();

            allowDispatcherToFinish.countDown();
            future.get(1, TimeUnit.SECONDS);
        } finally {
            allowDispatcherToFinish.countDown();
            executor.shutdownNow();
        }
    }

    private CheckSchedule schedule(Long id, CheckType checkType) {
        CheckSchedule schedule = new CheckSchedule();
        schedule.setId(id);
        schedule.setCheckType(checkType);
        schedule.setEnabled(true);
        return schedule;
    }
}
