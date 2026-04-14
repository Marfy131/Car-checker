package com.carwatch.application.schedule;

import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import com.carwatch.domain.schedule.CheckType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleManagementService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleManagementService.class);

    private final CheckScheduleRepository checkScheduleRepository;
    private final ObjectProvider<ScheduleDispatcher> scheduleDispatcher;
    private final Clock clock;

    public ScheduleManagementService(
            CheckScheduleRepository checkScheduleRepository,
            ObjectProvider<ScheduleDispatcher> scheduleDispatcher,
            Clock clock
    ) {
        this.checkScheduleRepository = checkScheduleRepository;
        this.scheduleDispatcher = scheduleDispatcher;
        this.clock = clock;
    }

    public List<CheckSchedule> findAll() {
        return checkScheduleRepository.findAll();
    }

    public CheckSchedule findById(Long id) {
        return checkScheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));
    }

    @Transactional
    public CheckSchedule updateSchedule(Long id, String cronExpression, int warningDaysBefore, boolean enabled) {
        CheckSchedule schedule = findById(id);
        schedule.setCronExpression(cronExpression);
        schedule.setWarningDaysBefore(warningDaysBefore);
        schedule.setEnabled(enabled);
        LocalDateTime now = LocalDateTime.now(clock);
        schedule.setUpdatedAt(now);
        if (enabled) {
            schedule.setNextRunAt(now);
        }
        return checkScheduleRepository.save(schedule);
    }

    public void runNow(Long id) {
        CheckSchedule schedule = findById(id);
        LocalDateTime triggerAt = LocalDateTime.now(clock).minusSeconds(1);
        logger.info(
                "Manually triggering schedule run scheduleId={} carId={} checkType={} triggerAt={}",
                schedule.getId(),
                schedule.getCarId(),
                schedule.getCheckType(),
                triggerAt
        );
        schedule.setNextRunAt(triggerAt);
        checkScheduleRepository.save(schedule);
        if (schedule.getCheckType() == CheckType.DAILY_REMINDER_SCAN || schedule.getCheckType() == CheckType.DAILY_SUMMARY_EMAIL) {
            ScheduleDispatcher dispatcher = scheduleDispatcher.getIfAvailable();
            if (dispatcher != null) {
                dispatcher.pollAndExecute();
            }
        }
    }
}
