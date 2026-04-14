package com.carwatch.application.schedule;

import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ScheduleClaimService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleClaimService.class);

    private final CheckScheduleRepository checkScheduleRepository;
    private final String lockOwnerPrefix;
    private final Clock clock;
    private final Duration claimLockTtl;

    @Autowired
    public ScheduleClaimService(
        CheckScheduleRepository checkScheduleRepository,
        Clock clock,
        @Value("${carwatch.scheduler.claim-lock-ttl:PT5M}") Duration claimLockTtl
    ) {
        this.checkScheduleRepository = checkScheduleRepository;
        this.clock = clock;
        this.claimLockTtl = claimLockTtl;
        this.lockOwnerPrefix = resolveHostName() + "-" + UUID.randomUUID();
    }

    ScheduleClaimService(CheckScheduleRepository checkScheduleRepository, Clock clock) {
        this(checkScheduleRepository, clock, Duration.ofMinutes(5));
    }

    @Transactional
    public boolean claim(CheckSchedule schedule) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<CheckSchedule> currentOptional = checkScheduleRepository.findById(schedule.getId());
        if (currentOptional.isEmpty()) {
            return false;
        }

        CheckSchedule current = currentOptional.get();
        if (!isClaimable(current, now)) {
            return false;
        }

        current.setLockUntil(now.plus(claimLockTtl));
        current.setLockOwner(lockOwner());
        current.setUpdatedAt(now);

        try {
            CheckSchedule claimed = checkScheduleRepository.save(current);
            copyState(claimed, schedule);
            return true;
        } catch (OptimisticLockingFailureException _) {
            logger.debug("Failed to claim schedule {} due to optimistic locking conflict", schedule.getId());
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
        current.setUpdatedAt(LocalDateTime.now(clock));

        try {
            CheckSchedule released = checkScheduleRepository.save(current);
            copyState(released, schedule);
        } catch (OptimisticLockingFailureException _) {
            logger.debug("Failed to release schedule {} due to optimistic locking conflict, another worker took over", schedule.getId());
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
        } catch (UnknownHostException _) {
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
