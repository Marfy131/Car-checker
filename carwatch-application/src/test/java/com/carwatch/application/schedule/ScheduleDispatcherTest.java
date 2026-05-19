package com.carwatch.application.schedule;

import com.carwatch.domain.car.Car;
import com.carwatch.domain.car.CarRepository;
import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleDispatcherTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-10T08:15:30Z"), ZoneOffset.UTC);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 1, 10, 8, 15, 30);

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

    private final ExecutorService scheduleExecutionExecutor = new AbstractExecutorService() {
        @Override
        public void shutdown() {
            // No-op: this test executor runs tasks immediately on the current thread and owns no resources.
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    @Test
    void pollAndExecuteDoesNothingWhenNoDueSchedulesExist() {
        when(checkScheduleRepository.findDueSchedules(FIXED_NOW)).thenReturn(List.of());

        ScheduleDispatcher scheduleDispatcher = scheduleDispatcher();

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

        when(checkScheduleRepository.findDueSchedules(FIXED_NOW)).thenReturn(List.of(schedule));
        when(scheduleClaimService.claim(schedule)).thenReturn(true);
        when(carRepository.findById(100L)).thenReturn(Optional.of(car));
        when(checkExecutor.execute(any(CheckCommand.class))).thenReturn(outcome);
        when(nextRunCalculator.calculateNext("0 0 6 * * *", "Europe/Bratislava"))
            .thenReturn(LocalDateTime.of(2026, 1, 11, 6, 0));

        ScheduleDispatcher scheduleDispatcher = scheduleDispatcher();

        scheduleDispatcher.pollAndExecute();

        ArgumentCaptor<CheckCommand> commandCaptor = ArgumentCaptor.forClass(CheckCommand.class);
        verify(checkExecutor).execute(commandCaptor.capture());
        CheckCommand command = commandCaptor.getValue();
        assertThat(command.carId()).isEqualTo(100L);
        assertThat(command.licensePlate()).isEqualTo("BA123AA");
        assertThat(command.vin()).isEqualTo("VIN123");
        assertThat(command.checkType()).isEqualTo(CheckType.PZP_CHECK);

        verify(runLoggingService).logRun(eq(schedule), eq(outcome), eq(FIXED_NOW), any(Long.class));
        verify(obligationUpdateService).updateFromOutcome(schedule, outcome);
        verify(checkScheduleRepository).save(schedule);
        verify(scheduleClaimService).release(schedule);
        assertThat(schedule.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void pollAndExecuteProcessesMultipleClaimedDueSchedules() {
        CheckSchedule firstSchedule = dueSchedule(1L, 100L);
        CheckSchedule secondSchedule = dueSchedule(2L, 200L);

        Car firstCar = car(100L, "BA123AA", "VIN123");
        Car secondCar = car(200L, "TT456BB", "VIN456");
        CheckOutcome outcome = new CheckOutcome(RunStatus.SUCCESS, "done", null, "{\"ok\":true}");

        when(checkScheduleRepository.findDueSchedules(FIXED_NOW)).thenReturn(List.of(firstSchedule, secondSchedule));
        when(scheduleClaimService.claim(firstSchedule)).thenReturn(true);
        when(scheduleClaimService.claim(secondSchedule)).thenReturn(true);
        when(carRepository.findById(100L)).thenReturn(Optional.of(firstCar));
        when(carRepository.findById(200L)).thenReturn(Optional.of(secondCar));
        when(checkExecutor.execute(any(CheckCommand.class))).thenReturn(outcome);
        when(nextRunCalculator.calculateNext("0 0 6 * * *", "Europe/Bratislava"))
            .thenReturn(
                LocalDateTime.of(2026, 1, 11, 6, 0),
                LocalDateTime.of(2026, 1, 12, 6, 0)
            );

        ScheduleDispatcher scheduleDispatcher = scheduleDispatcher();

        scheduleDispatcher.pollAndExecute();

        ArgumentCaptor<CheckCommand> commandCaptor = ArgumentCaptor.forClass(CheckCommand.class);
        verify(checkExecutor, times(2)).execute(commandCaptor.capture());
        assertThat(commandCaptor.getAllValues())
            .extracting(CheckCommand::carId, CheckCommand::licensePlate, CheckCommand::vin, CheckCommand::checkType)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(100L, "BA123AA", "VIN123", CheckType.PZP_CHECK),
                org.assertj.core.groups.Tuple.tuple(200L, "TT456BB", "VIN456", CheckType.PZP_CHECK)
            );

        verify(runLoggingService).logRun(eq(firstSchedule), eq(outcome), eq(FIXED_NOW), any(Long.class));
        verify(runLoggingService).logRun(eq(secondSchedule), eq(outcome), eq(FIXED_NOW), any(Long.class));
        verify(obligationUpdateService).updateFromOutcome(firstSchedule, outcome);
        verify(obligationUpdateService).updateFromOutcome(secondSchedule, outcome);
        verify(checkScheduleRepository).save(firstSchedule);
        verify(checkScheduleRepository).save(secondSchedule);
        verify(scheduleClaimService).release(firstSchedule);
        verify(scheduleClaimService).release(secondSchedule);
        assertThat(firstSchedule.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(secondSchedule.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void pollAndExecuteContinuesWithOtherDueSchedulesWhenOneScheduleSaveFails() {
        CheckSchedule firstSchedule = dueSchedule(1L, 100L);
        CheckSchedule secondSchedule = dueSchedule(2L, 200L);

        Car firstCar = car(100L, "BA123AA", "VIN123");
        Car secondCar = car(200L, "TT456BB", "VIN456");
        CheckOutcome outcome = new CheckOutcome(RunStatus.SUCCESS, "done", null, "{\"ok\":true}");

        when(checkScheduleRepository.findDueSchedules(FIXED_NOW)).thenReturn(List.of(firstSchedule, secondSchedule));
        when(scheduleClaimService.claim(firstSchedule)).thenReturn(true);
        when(scheduleClaimService.claim(secondSchedule)).thenReturn(true);
        when(carRepository.findById(100L)).thenReturn(Optional.of(firstCar));
        when(carRepository.findById(200L)).thenReturn(Optional.of(secondCar));
        when(checkExecutor.execute(any(CheckCommand.class))).thenReturn(outcome);
        when(nextRunCalculator.calculateNext("0 0 6 * * *", "Europe/Bratislava"))
            .thenReturn(
                LocalDateTime.of(2026, 1, 11, 6, 0),
                LocalDateTime.of(2026, 1, 12, 6, 0)
            );
        when(checkScheduleRepository.save(firstSchedule)).thenThrow(new IllegalStateException("save failed"));

        ScheduleDispatcher scheduleDispatcher = scheduleDispatcher();

        scheduleDispatcher.pollAndExecute();

        verify(runLoggingService).logRun(eq(firstSchedule), eq(outcome), eq(FIXED_NOW), any(Long.class));
        verify(runLoggingService).logRun(eq(secondSchedule), eq(outcome), eq(FIXED_NOW), any(Long.class));
        verify(obligationUpdateService).updateFromOutcome(firstSchedule, outcome);
        verify(obligationUpdateService).updateFromOutcome(secondSchedule, outcome);
        verify(checkScheduleRepository).save(firstSchedule);
        verify(checkScheduleRepository).save(secondSchedule);
        verify(scheduleClaimService).release(firstSchedule);
        verify(scheduleClaimService).release(secondSchedule);
    }

    private CheckSchedule dueSchedule() {
        return dueSchedule(1L, 100L);
    }

    private CheckSchedule dueSchedule(Long id, Long carId) {
        CheckSchedule schedule = new CheckSchedule();
        schedule.setId(id);
        schedule.setCarId(carId);
        schedule.setCheckType(CheckType.PZP_CHECK);
        schedule.setCronExpression("0 0 6 * * *");
        schedule.setZoneId("Europe/Bratislava");
        schedule.setEnabled(true);
        schedule.setWarningDaysBefore(14);
        schedule.setNextRunAt(FIXED_NOW.minusMinutes(2));
        return schedule;
    }

    private Car car(Long id, String licensePlate, String vin) {
        Car car = new Car();
        car.setId(id);
        car.setLicensePlate(licensePlate);
        car.setVin(vin);
        return car;
    }

    private ScheduleDispatcher scheduleDispatcher() {
        try {
            var constructor = Arrays.stream(ScheduleDispatcher.class.getConstructors())
                .findFirst()
                .orElseThrow();
            Object[] arguments = Arrays.stream(constructor.getParameterTypes())
                .map(this::constructorArgumentFor)
                .toArray();
            return (ScheduleDispatcher) constructor.newInstance(arguments);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to construct ScheduleDispatcher for test", ex);
        }
    }

    private Object constructorArgumentFor(Class<?> parameterType) {
        if (parameterType == CheckScheduleRepository.class) {
            return checkScheduleRepository;
        }
        if (parameterType == CarRepository.class) {
            return carRepository;
        }
        if (parameterType == ScheduleClaimService.class) {
            return scheduleClaimService;
        }
        if (parameterType == CheckExecutor.class) {
            return checkExecutor;
        }
        if (parameterType == RunLoggingService.class) {
            return runLoggingService;
        }
        if (parameterType == ObligationUpdateService.class) {
            return obligationUpdateService;
        }
        if (parameterType == NextRunCalculator.class) {
            return nextRunCalculator;
        }
        if (parameterType == ExecutorService.class) {
            return scheduleExecutionExecutor;
        }
        if (parameterType == Clock.class) {
            return FIXED_CLOCK;
        }
        if (parameterType == int.class || parameterType == Integer.class) {
            return 2;
        }
        throw new AssertionError("Unsupported ScheduleDispatcher constructor parameter: " + parameterType.getName());
    }
}
