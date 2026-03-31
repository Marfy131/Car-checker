package com.carwatch.application.schedule;

import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleClaimService {

    private final CheckScheduleRepository checkScheduleRepository;
    private final String lockOwnerPrefix;

    public ScheduleClaimService(CheckScheduleRepository checkScheduleRepository) {
        this.checkScheduleRepository = checkScheduleRepository;
        this.lockOwnerPrefix = resolveHostName() + "-" + UUID.randomUUID();
    }

    @Transactional
    public boolean claim(CheckSchedule schedule) {
        LocalDateTime now = LocalDateTime.now();
        Optional<CheckSchedule> currentOptional = checkScheduleRepository.findById(schedule.getId());
        if (currentOptional.isEmpty()) {
            return false;
        }

        CheckSchedule current = currentOptional.get();
        if (!isClaimable(current, now)) {
            return false;
        }

        current.setLockUntil(now.plusMinutes(5));
        current.setLockOwner(lockOwner());
        current.setUpdatedAt(now);

        try {
            CheckSchedule claimed = checkScheduleRepository.save(current);
            copyState(claimed, schedule);
            return true;
        } catch (OptimisticLockingFailureException ex) {
            return false;
        }
    }

    @Transactional
    public void release(CheckSchedule schedule) {
        Optional<CheckSchedule> currentOptional = checkScheduleRepository.findById(schedule.getId());
        if (currentOptional.isEmpty()) {
            return;
        }

        CheckSchedule current = currentOptional.get();
        if (schedule.getLockOwner() != null && !schedule.getLockOwner().equals(current.getLockOwner())) {
            return;
        }

        current.setLockUntil(null);
        current.setLockOwner(null);
        current.setUpdatedAt(LocalDateTime.now());

        try {
            CheckSchedule released = checkScheduleRepository.save(current);
            copyState(released, schedule);
        } catch (OptimisticLockingFailureException ex) {
            // another worker already modified the schedule; release can be ignored here
        }
    }

    private boolean isClaimable(CheckSchedule schedule, LocalDateTime now) {
        if (!schedule.isEnabled() || schedule.getNextRunAt() == null || schedule.getNextRunAt().isAfter(now)) {
            return false;
        }
        return schedule.getLockUntil() == null || schedule.getLockUntil().isBefore(now);
    }

    private String lockOwner() {
        return lockOwnerPrefix + "-thread-" + Thread.currentThread().threadId();
    }

    private String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            return "unknown-host";
        }
    }

    private void copyState(CheckSchedule source, CheckSchedule target) {
        target.setVersion(source.getVersion());
        target.setLockUntil(source.getLockUntil());
        target.setLockOwner(source.getLockOwner());
        target.setNextRunAt(source.getNextRunAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }
}
