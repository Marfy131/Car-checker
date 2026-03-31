package com.carwatch.application.schedule;

import com.carwatch.domain.car.Car;
import com.carwatch.domain.car.CarRepository;
import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleDispatcherTest {

    @Mock
    private CheckScheduleRepository checkScheduleRepository;
    @Mock
    private CarRepository carRepository;
    @Mock
    private ScheduleClaimService scheduleClaimService;
    @Mock
    private CheckExecutor checkExecutor;
    @Mock
    private RunLoggingService runLoggingService;
    @Mock
    private ObligationUpdateService obligationUpdateService;
    @Mock
    private NextRunCalculator nextRunCalculator;

    @InjectMocks
    private ScheduleDispatcher scheduleDispatcher;

    @Test
    void pollAndExecuteDoesNothingWhenNoDueSchedulesExist() {
        when(checkScheduleRepository.findDueSchedules(any(LocalDateTime.class))).thenReturn(List.of());

        scheduleDispatcher.pollAndExecute();

        verify(scheduleClaimService, never()).claim(any());
        verify(checkExecutor, never()).execute(any());
        verify(runLoggingService, never()).logRun(any(), any(), any(), any(Long.class));
        verify(scheduleClaimService, never()).release(any());
    }

    @Test
    void pollAndExecuteClaimsExecutesLogsAndReleasesDueSchedule() {
        CheckSchedule schedule = dueSchedule();
        Car car = new Car();
        car.setId(100L);
        car.setLicensePlate("BA123AA");
        car.setVin("VIN123");

        CheckOutcome outcome = new CheckOutcome(RunStatus.SUCCESS, "done", null, "{\"ok\":true}");

        when(checkScheduleRepository.findDueSchedules(any(LocalDateTime.class))).thenReturn(List.of(schedule));
        when(scheduleClaimService.claim(schedule)).thenReturn(true);
        when(carRepository.findById(100L)).thenReturn(Optional.of(car));
        when(checkExecutor.execute(any(CheckCommand.class))).thenReturn(outcome);
        when(nextRunCalculator.calculateNext("0 0 6 * * *", "Europe/Bratislava"))
            .thenReturn(LocalDateTime.of(2026, 1, 11, 6, 0));

        scheduleDispatcher.pollAndExecute();

        ArgumentCaptor<CheckCommand> commandCaptor = ArgumentCaptor.forClass(CheckCommand.class);
        verify(checkExecutor).execute(commandCaptor.capture());
        CheckCommand command = commandCaptor.getValue();
        assertThat(command.carId()).isEqualTo(100L);
        assertThat(command.licensePlate()).isEqualTo("BA123AA");
        assertThat(command.vin()).isEqualTo("VIN123");
        assertThat(command.checkType()).isEqualTo(CheckType.PZP_CHECK);

        verify(runLoggingService).logRun(any(CheckSchedule.class), any(CheckOutcome.class), any(LocalDateTime.class), any(Long.class));
        verify(obligationUpdateService).updateFromOutcome(schedule, outcome);
        verify(checkScheduleRepository).save(schedule);
        verify(scheduleClaimService).release(schedule);
    }

    private CheckSchedule dueSchedule() {
        CheckSchedule schedule = new CheckSchedule();
        schedule.setId(1L);
        schedule.setCarId(100L);
        schedule.setCheckType(CheckType.PZP_CHECK);
        schedule.setCronExpression("0 0 6 * * *");
        schedule.setZoneId("Europe/Bratislava");
        schedule.setEnabled(true);
        schedule.setWarningDaysBefore(14);
        schedule.setNextRunAt(LocalDateTime.now().minusMinutes(2));
        return schedule;
    }
}
