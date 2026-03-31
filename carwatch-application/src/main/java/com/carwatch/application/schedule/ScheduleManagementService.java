package com.carwatch.application.schedule;

import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import com.carwatch.domain.schedule.CheckType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleManagementService {

    private final CheckScheduleRepository checkScheduleRepository;
    private final ObjectProvider<ScheduleDispatcher> scheduleDispatcher;

    public ScheduleManagementService(
            CheckScheduleRepository checkScheduleRepository,
            ObjectProvider<ScheduleDispatcher> scheduleDispatcher
    ) {
        this.checkScheduleRepository = checkScheduleRepository;
        this.scheduleDispatcher = scheduleDispatcher;
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
        schedule.setUpdatedAt(LocalDateTime.now());
        if (enabled) {
            schedule.setNextRunAt(LocalDateTime.now());
        }
        return checkScheduleRepository.save(schedule);
    }

    public void runNow(Long id) {
        CheckSchedule schedule = findById(id);
        schedule.setNextRunAt(LocalDateTime.now().minusSeconds(1));
        checkScheduleRepository.save(schedule);
        if (schedule.getCheckType() == CheckType.DAILY_REMINDER_SCAN || schedule.getCheckType() == CheckType.DAILY_SUMMARY_EMAIL) {
            ScheduleDispatcher dispatcher = scheduleDispatcher.getIfAvailable();
            if (dispatcher != null) {
                dispatcher.pollAndExecute();
            }
        }
    }
}
