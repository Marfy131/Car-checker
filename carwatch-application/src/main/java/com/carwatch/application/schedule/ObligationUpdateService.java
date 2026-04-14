package com.carwatch.application.schedule;

import com.carwatch.domain.obligation.ExpiryStatus;
import com.carwatch.domain.obligation.ObligationState;
import com.carwatch.domain.obligation.ObligationStateRepository;
import com.carwatch.domain.obligation.ObligationType;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckTypeMapping;
import com.carwatch.domain.schedule.RunStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObligationUpdateService {

    private static final Logger logger = LoggerFactory.getLogger(ObligationUpdateService.class);

    private static final String SOURCE_SYSTEM = "scheduler";

    private final ObligationStateRepository obligationStateRepository;
    private final Clock clock;

    public ObligationUpdateService(ObligationStateRepository obligationStateRepository, Clock clock) {
        this.obligationStateRepository = obligationStateRepository;
        this.clock = clock;
    }

    @Transactional
    public void updateFromOutcome(CheckSchedule schedule, CheckOutcome outcome) {
        ObligationType obligationType = CheckTypeMapping.findObligationType(schedule.getCheckType()).orElse(null);
        if (obligationType == null || schedule.getCarId() == null) {
            return;
        }

        ObligationState state = obligationStateRepository
            .findByCarIdAndObligationType(schedule.getCarId(), obligationType)
            .orElseGet(() -> initState(schedule.getCarId(), obligationType));

        LocalDateTime now = LocalDateTime.now(clock);
        state.setLastCheckedAt(now);
        state.setSourceSystem(SOURCE_SYSTEM);
        state.setDetailsJson(outcome.findingsJson());

        LocalDate previousExpiryDate = state.getExpiryDate();
        LocalDate expiryDate = outcome.expiryDateFound();

        if (outcome.status() == RunStatus.SUCCESS && expiryDate != null) {
            state.setExpiryDate(expiryDate);
            state.setLastSuccessfulCheckAt(now);
            state.setStatus(resolveExpiryStatus(expiryDate, schedule.getWarningDaysBefore()));
        } else if (outcome.status() == RunStatus.ERROR) {
            state.setExpiryDate(previousExpiryDate);
            state.setStatus(ExpiryStatus.ERROR);
        } else if (expiryDate == null) {
            state.setExpiryDate(previousExpiryDate);
            state.setStatus(ExpiryStatus.UNKNOWN);
        } else {
            state.setExpiryDate(expiryDate);
        }

        state.setUpdatedAt(now);
        obligationStateRepository.save(state);

        if (outcome.status() == RunStatus.ERROR || outcome.expiryDateFound() == null) {
            logger.warn(
                    "Updated obligation from non-successful outcome carId={} checkType={} obligationType={} outcomeStatus={} obligationStatus={} expiryDate={}",
                    schedule.getCarId(),
                    schedule.getCheckType(),
                    obligationType,
                    outcome.status(),
                    state.getStatus(),
                    state.getExpiryDate()
            );
            return;
        }

        logger.debug(
                "Updated obligation state carId={} checkType={} obligationType={} outcomeStatus={} obligationStatus={} expiryDate={}",
                schedule.getCarId(),
                schedule.getCheckType(),
                obligationType,
                outcome.status(),
                state.getStatus(),
                state.getExpiryDate()
        );
    }

    private ObligationState initState(Long carId, ObligationType obligationType) {
        LocalDateTime now = LocalDateTime.now(clock);
        ObligationState state = new ObligationState();
        state.setCarId(carId);
        state.setObligationType(obligationType);
        state.setStatus(ExpiryStatus.UNKNOWN);
        state.setCreatedAt(now);
        state.setUpdatedAt(now);
        return state;
    }

    private ExpiryStatus resolveExpiryStatus(LocalDate expiryDate, int warningDaysBefore) {
        LocalDate today = LocalDate.now(clock);
        if (expiryDate.isBefore(today)) {
            return ExpiryStatus.EXPIRED;
        }

        long daysUntilExpiry = ChronoUnit.DAYS.between(today, expiryDate);
        if (daysUntilExpiry <= warningDaysBefore) {
            return ExpiryStatus.EXPIRING;
        }
        return ExpiryStatus.VALID;
    }
}
