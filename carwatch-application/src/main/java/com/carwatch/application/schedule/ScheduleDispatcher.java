package com.carwatch.application.schedule;

import com.carwatch.domain.car.Car;
import com.carwatch.domain.car.CarRepository;
import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import com.carwatch.domain.schedule.RunStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "carwatch.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduleDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleDispatcher.class);

    private final CheckScheduleRepository checkScheduleRepository;
    private final CarRepository carRepository;
    private final ScheduleClaimService scheduleClaimService;
    private final CheckExecutor checkExecutor;
    private final RunLoggingService runLoggingService;
    private final ObligationUpdateService obligationUpdateService;
    private final NextRunCalculator nextRunCalculator;
    private final Clock clock;
    private final ExecutorService scheduleExecutionExecutor;
    private final int executionParallelism;

    public ScheduleDispatcher(
        CheckScheduleRepository checkScheduleRepository,
        CarRepository carRepository,
        ScheduleClaimService scheduleClaimService,
        CheckExecutor checkExecutor,
        RunLoggingService runLoggingService,
        ObligationUpdateService obligationUpdateService,
        NextRunCalculator nextRunCalculator,
        Clock clock,
        @Qualifier("scheduleExecutionExecutor") ExecutorService scheduleExecutionExecutor,
        @Value("${carwatch.scheduler.execution-parallelism:2}") int executionParallelism
    ) {
        this.checkScheduleRepository = checkScheduleRepository;
        this.carRepository = carRepository;
        this.scheduleClaimService = scheduleClaimService;
        this.checkExecutor = checkExecutor;
        this.runLoggingService = runLoggingService;
        this.obligationUpdateService = obligationUpdateService;
        this.nextRunCalculator = nextRunCalculator;
        this.clock = clock;
        this.scheduleExecutionExecutor = scheduleExecutionExecutor;
        this.executionParallelism = Math.max(1, executionParallelism);
    }

    @Scheduled(fixedDelayString = "${carwatch.scheduler.poll-delay-ms:30000}")
    public void pollAndExecute() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CheckSchedule> dueSchedules = checkScheduleRepository.findDueSchedules(now);
        for (int start = 0; start < dueSchedules.size(); start += executionParallelism) {
            List<Future<?>> batch = submitBatch(dueSchedules, start);
            if (!waitForBatch(batch)) {
                return;
            }
        }
    }

    private List<Future<?>> submitBatch(List<CheckSchedule> dueSchedules, int start) {
        int endExclusive = Math.min(start + executionParallelism, dueSchedules.size());
        List<Future<?>> batch = new ArrayList<>(endExclusive - start);
        for (int index = start; index < endExclusive; index++) {
            CheckSchedule schedule = dueSchedules.get(index);
            batch.add(scheduleExecutionExecutor.submit(() -> processScheduleSafely(schedule)));
        }
        return batch;
    }

    private boolean waitForBatch(List<Future<?>> batch) {
        for (Future<?> future : batch) {
            try {
                future.get();
            } catch (InterruptedException ex) {
                cancelBatch(batch);
                Thread.currentThread().interrupt();
                logger.warn("Schedule polling interrupted while waiting for worker completion", ex);
                return false;
            } catch (ExecutionException ex) {
                logger.error("Unexpected worker failure while processing schedules", ex.getCause());
            }
        }
        return true;
    }

    private void cancelBatch(List<Future<?>> batch) {
        for (Future<?> future : batch) {
            future.cancel(true);
        }
    }

    private void processScheduleSafely(CheckSchedule schedule) {
        try {
            processSchedule(schedule);
        } catch (Exception ex) {
            logger.error("Failed to process schedule {}", schedule.getId(), ex);
        }
    }

    private void processSchedule(CheckSchedule schedule) {
        if (!scheduleClaimService.claim(schedule)) {
            return;
        }

        try {
            LocalDateTime startedAt = LocalDateTime.now(clock);
            long startNanos = System.nanoTime();
            CheckOutcome outcome;

            try {
                CheckCommand command = createCommand(schedule);
                outcome = checkExecutor.execute(command);
            } catch (Exception ex) {
                outcome = new CheckOutcome(RunStatus.ERROR, ex.getMessage(), null, null);
            }

            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            runLoggingService.logRun(schedule, outcome, startedAt, durationMs);

            try {
                obligationUpdateService.updateFromOutcome(schedule, outcome);
            } catch (Exception ex) {
                logger.error("Failed to update obligation for schedule {}", schedule.getId(), ex);
            }

            if (schedule.getCronExpression() != null && !schedule.getCronExpression().isBlank()) {
                schedule.setNextRunAt(nextRunCalculator.calculateNext(schedule.getCronExpression(), schedule.getZoneId()));
            } else {
                schedule.setNextRunAt(null);
            }
            schedule.setUpdatedAt(LocalDateTime.now(clock));
            checkScheduleRepository.save(schedule);
        } finally {
            scheduleClaimService.release(schedule);
        }
    }

    private CheckCommand createCommand(CheckSchedule schedule) {
        Car car = null;
        if (schedule.getCarId() != null) {
            car = carRepository.findById(schedule.getCarId()).orElse(null);
        }

        String licensePlate = car != null ? car.getLicensePlate() : null;
        String vin = car != null ? car.getVin() : null;
        return new CheckCommand(schedule.getCarId(), licensePlate, vin, schedule.getCheckType());
    }
}
