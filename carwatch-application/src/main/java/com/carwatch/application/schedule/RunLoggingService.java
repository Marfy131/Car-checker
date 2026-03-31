package com.carwatch.application.schedule;

import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckRunLog;
import com.carwatch.domain.schedule.CheckRunLogRepository;
import com.carwatch.domain.schedule.CheckSchedule;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunLoggingService {

    private final CheckRunLogRepository checkRunLogRepository;

    public RunLoggingService(CheckRunLogRepository checkRunLogRepository) {
        this.checkRunLogRepository = checkRunLogRepository;
    }

    @Transactional
    public CheckRunLog logRun(CheckSchedule schedule, CheckOutcome outcome, LocalDateTime startedAt, long durationMs) {
        CheckRunLog log = new CheckRunLog();
        log.setScheduleId(schedule.getId());
        log.setCarId(schedule.getCarId());
        log.setInsurancePolicyId(schedule.getInsurancePolicyId());
        log.setCheckType(schedule.getCheckType());
        log.setStartedAt(startedAt);
        log.setFinishedAt(startedAt.plus(durationMs, ChronoUnit.MILLIS));
        log.setStatus(outcome.status());
        log.setMessage(outcome.message());
        log.setExpiryDateFound(outcome.expiryDateFound());
        log.setFindingsJson(outcome.findingsJson());
        log.setDurationMs(durationMs);
        return checkRunLogRepository.save(log);
    }
}
