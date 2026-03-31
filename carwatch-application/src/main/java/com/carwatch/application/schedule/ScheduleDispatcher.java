package com.carwatch.application.schedule;

import com.carwatch.domain.car.Car;
import com.carwatch.domain.car.CarRepository;
import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import com.carwatch.domain.schedule.RunStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "carwatch.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduleDispatcher {

    private final CheckScheduleRepository checkScheduleRepository;
    private final CarRepository carRepository;
    private final ScheduleClaimService scheduleClaimService;
    private final CheckExecutor checkExecutor;
    private final RunLoggingService runLoggingService;
    private final ObligationUpdateService obligationUpdateService;
    private final NextRunCalculator nextRunCalculator;

    public ScheduleDispatcher(
        CheckScheduleRepository checkScheduleRepository,
        CarRepository carRepository,
        ScheduleClaimService scheduleClaimService,
        CheckExecutor checkExecutor,
        RunLoggingService runLoggingService,
        ObligationUpdateService obligationUpdateService,
        NextRunCalculator nextRunCalculator
    ) {
        this.checkScheduleRepository = checkScheduleRepository;
        this.carRepository = carRepository;
        this.scheduleClaimService = scheduleClaimService;
        this.checkExecutor = checkExecutor;
        this.runLoggingService = runLoggingService;
        this.obligationUpdateService = obligationUpdateService;
        this.nextRunCalculator = nextRunCalculator;
    }

    @Scheduled(fixedDelay = 30000)
    public void pollAndExecute() {
        LocalDateTime now = LocalDateTime.now();
        List<CheckSchedule> dueSchedules = checkScheduleRepository.findDueSchedules(now);
        for (CheckSchedule schedule : dueSchedules) {
            try {
                processSchedule(schedule);
            } catch (Exception ignored) {
                // the dispatcher must continue with remaining schedules
            }
        }
    }

    private void processSchedule(CheckSchedule schedule) {
        if (!scheduleClaimService.claim(schedule)) {
            return;
        }

        try {
            LocalDateTime startedAt = LocalDateTime.now();
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
            } catch (Exception ignored) {
                // obligation updates should not break scheduling lifecycle
            }

            if (schedule.getCronExpression() != null && !schedule.getCronExpression().isBlank()) {
                schedule.setNextRunAt(nextRunCalculator.calculateNext(schedule.getCronExpression(), schedule.getZoneId()));
            } else {
                schedule.setNextRunAt(null);
            }
            schedule.setUpdatedAt(LocalDateTime.now());
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
