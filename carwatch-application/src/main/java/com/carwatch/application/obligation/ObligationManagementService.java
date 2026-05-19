package com.carwatch.application.obligation;

import com.carwatch.domain.insurance.InsurancePolicy;
import com.carwatch.domain.insurance.InsurancePolicyRepository;
import com.carwatch.domain.insurance.PolicyType;
import com.carwatch.domain.obligation.ExpiryStatus;
import com.carwatch.domain.obligation.ObligationState;
import com.carwatch.domain.obligation.ObligationStateRepository;
import com.carwatch.domain.obligation.ObligationType;
import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.CheckTypeMapping;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObligationManagementService {

    private final InsurancePolicyRepository insurancePolicyRepository;
    private final ObligationStateRepository obligationStateRepository;
    private final CheckScheduleRepository checkScheduleRepository;
    private final Clock clock;

    public ObligationManagementService(
            InsurancePolicyRepository insurancePolicyRepository,
            ObligationStateRepository obligationStateRepository,
            CheckScheduleRepository checkScheduleRepository,
            Clock clock) {
        this.insurancePolicyRepository = insurancePolicyRepository;
        this.obligationStateRepository = obligationStateRepository;
        this.checkScheduleRepository = checkScheduleRepository;
        this.clock = clock;
    }

    public ObligationForm buildForm(Long carId) {
        ObligationForm form = new ObligationForm();

        for (InsurancePolicy policy : insurancePolicyRepository.findByCarId(carId)) {
            if (policy.getPolicyType() == PolicyType.PZP) {
                form.setPzpExpiry(policy.getExpiryDate());
            } else if (policy.getPolicyType() == PolicyType.COLLISION) {
                form.setCollisionExpiry(policy.getExpiryDate());
            }
        }

        for (ObligationState state : obligationStateRepository.findByCarId(carId)) {
            switch (state.getObligationType()) {
                case STK -> form.setStkExpiry(state.getExpiryDate());
                case EK -> form.setEkExpiry(state.getExpiryDate());
                case VIGNETTE_SK -> form.setVignetteSkExpiry(state.getExpiryDate());
                case VIGNETTE_CZ -> form.setVignetteCzExpiry(state.getExpiryDate());
                case VIGNETTE_AT -> form.setVignetteAtExpiry(state.getExpiryDate());
                case VIGNETTE_HU -> form.setVignetteHuExpiry(state.getExpiryDate());
                default -> { /* PZP/COLLISION come from InsurancePolicy */ }
            }
        }

        return form;
    }

    public Set<ObligationType> findActiveTypes(Long carId) {
        Set<ObligationType> types = new LinkedHashSet<>();
        for (InsurancePolicy policy : insurancePolicyRepository.findByCarId(carId)) {
            if (policy.getPolicyType() == PolicyType.PZP) types.add(ObligationType.PZP);
            if (policy.getPolicyType() == PolicyType.COLLISION) types.add(ObligationType.COLLISION);
        }
        for (ObligationState state : obligationStateRepository.findByCarId(carId)) {
            types.add(state.getObligationType());
        }
        return types;
    }

    @Transactional
    public void saveObligations(Long carId, ObligationForm form) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);

        Map<CheckType, Integer> warningDays = buildWarningDaysMap(carId);

        for (InsurancePolicy policy : insurancePolicyRepository.findByCarId(carId)) {
            LocalDate expiry = policy.getPolicyType() == PolicyType.PZP
                    ? form.getPzpExpiry() : form.getCollisionExpiry();
            policy.setExpiryDate(expiry);
            policy.setStatus(resolveStatus(expiry, policy.getWarningDaysBefore(), today));
            insurancePolicyRepository.save(policy);

            ObligationType obligationType = policy.getPolicyType() == PolicyType.PZP
                    ? ObligationType.PZP : ObligationType.COLLISION;
            updateObligationState(carId, obligationType, expiry, policy.getWarningDaysBefore(), today, now);
        }

        for (ObligationState state : obligationStateRepository.findByCarId(carId)) {
            ObligationType type = state.getObligationType();
            if (type == ObligationType.PZP || type == ObligationType.COLLISION) {
                continue; // handled via InsurancePolicy above
            }
            LocalDate expiry = expiryFromForm(type, form);
            CheckType checkType = checkTypeFor(type);
            int days = checkType != null ? warningDays.getOrDefault(checkType, defaultWarningDays(type)) : defaultWarningDays(type);
            state.setExpiryDate(expiry);
            state.setStatus(resolveStatus(expiry, days, today));
            state.setManualOverride(expiry != null);
            state.setSourceSystem(expiry != null ? "manual" : null);
            state.setUpdatedAt(now);
            obligationStateRepository.save(state);
        }
    }

    private void updateObligationState(Long carId, ObligationType type, LocalDate expiry,
            int warningDays, LocalDate today, LocalDateTime now) {
        obligationStateRepository.findByCarIdAndObligationType(carId, type).ifPresent(state -> {
            state.setExpiryDate(expiry);
            state.setStatus(resolveStatus(expiry, warningDays, today));
            state.setManualOverride(expiry != null);
            state.setSourceSystem(expiry != null ? "manual" : null);
            state.setUpdatedAt(now);
            obligationStateRepository.save(state);
        });
    }

    private Map<CheckType, Integer> buildWarningDaysMap(Long carId) {
        Map<CheckType, Integer> map = new EnumMap<>(CheckType.class);
        for (CheckSchedule schedule : checkScheduleRepository.findByCarId(carId)) {
            map.put(schedule.getCheckType(), schedule.getWarningDaysBefore());
        }
        return map;
    }

    private ExpiryStatus resolveStatus(LocalDate expiryDate, int warningDaysBefore, LocalDate today) {
        if (expiryDate == null) return ExpiryStatus.UNKNOWN;
        if (expiryDate.isBefore(today)) return ExpiryStatus.EXPIRED;
        long daysUntil = ChronoUnit.DAYS.between(today, expiryDate);
        return daysUntil <= warningDaysBefore ? ExpiryStatus.EXPIRING : ExpiryStatus.VALID;
    }

    private LocalDate expiryFromForm(ObligationType type, ObligationForm form) {
        return switch (type) {
            case STK -> form.getStkExpiry();
            case EK -> form.getEkExpiry();
            case VIGNETTE_SK -> form.getVignetteSkExpiry();
            case VIGNETTE_CZ -> form.getVignetteCzExpiry();
            case VIGNETTE_AT -> form.getVignetteAtExpiry();
            case VIGNETTE_HU -> form.getVignetteHuExpiry();
            default -> null;
        };
    }

    private CheckType checkTypeFor(ObligationType type) {
        for (CheckType ct : CheckType.values()) {
            if (CheckTypeMapping.findObligationType(ct).map(t -> t == type).orElse(false)) {
                return ct;
            }
        }
        return null;
    }

    private int defaultWarningDays(ObligationType type) {
        return switch (type) {
            case STK, EK -> 30;
            case VIGNETTE_SK, VIGNETTE_CZ, VIGNETTE_AT, VIGNETTE_HU -> 7;
            default -> 14;
        };
    }
}
