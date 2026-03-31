package com.carwatch.application.schedule;

import com.carwatch.domain.obligation.ExpiryStatus;
import com.carwatch.domain.obligation.ObligationState;
import com.carwatch.domain.obligation.ObligationStateRepository;
import com.carwatch.domain.obligation.ObligationType;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObligationUpdateService {

    private static final String SOURCE_SYSTEM = "scheduler";

    private static final Map<CheckType, ObligationType> TYPE_MAPPING = new EnumMap<>(CheckType.class);

    static {
        TYPE_MAPPING.put(CheckType.PZP_CHECK, ObligationType.PZP);
        TYPE_MAPPING.put(CheckType.COLLISION_INSURANCE_CHECK, ObligationType.COLLISION);
        TYPE_MAPPING.put(CheckType.STK_CHECK, ObligationType.STK);
        TYPE_MAPPING.put(CheckType.EK_CHECK, ObligationType.EK);
        TYPE_MAPPING.put(CheckType.VIGNETTE_SK_CHECK, ObligationType.VIGNETTE_SK);
        TYPE_MAPPING.put(CheckType.VIGNETTE_CZ_CHECK, ObligationType.VIGNETTE_CZ);
        TYPE_MAPPING.put(CheckType.VIGNETTE_AT_CHECK, ObligationType.VIGNETTE_AT);
    }

    private final ObligationStateRepository obligationStateRepository;

    public ObligationUpdateService(ObligationStateRepository obligationStateRepository) {
        this.obligationStateRepository = obligationStateRepository;
    }

    @Transactional
    public void updateFromOutcome(CheckSchedule schedule, CheckOutcome outcome) {
        ObligationType obligationType = TYPE_MAPPING.get(schedule.getCheckType());
        if (obligationType == null || schedule.getCarId() == null) {
            return;
        }

        ObligationState state = obligationStateRepository
            .findByCarIdAndObligationType(schedule.getCarId(), obligationType)
            .orElseGet(() -> initState(schedule.getCarId(), obligationType));

        LocalDateTime now = LocalDateTime.now();
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
    }

    private ObligationState initState(Long carId, ObligationType obligationType) {
        LocalDateTime now = LocalDateTime.now();
        ObligationState state = new ObligationState();
        state.setCarId(carId);
        state.setObligationType(obligationType);
        state.setStatus(ExpiryStatus.UNKNOWN);
        state.setCreatedAt(now);
        state.setUpdatedAt(now);
        return state;
    }

    private ExpiryStatus resolveExpiryStatus(LocalDate expiryDate, int warningDaysBefore) {
        LocalDate today = LocalDate.now();
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
