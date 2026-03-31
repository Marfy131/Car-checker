package com.carwatch.application.car;

import com.carwatch.domain.car.Car;
import com.carwatch.domain.car.CarRepository;
import com.carwatch.domain.insurance.CheckMode;
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
import com.carwatch.domain.vignette.CarVignetteSelection;
import com.carwatch.domain.vignette.CarVignetteSelectionRepository;
import com.carwatch.domain.vignette.CountryCode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CarManagementService {

    private static final String DEFAULT_CRON = "0 0 6 * * *";
    private static final String DEFAULT_ZONE_ID = "Europe/Bratislava";
    private static final int INSURANCE_WARNING_DAYS = 14;
    private static final int STK_EK_WARNING_DAYS = 30;
    private static final int VIGNETTE_WARNING_DAYS = 7;

    private final CarRepository carRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final CarVignetteSelectionRepository carVignetteSelectionRepository;
    private final CheckScheduleRepository checkScheduleRepository;
    private final ObligationStateRepository obligationStateRepository;

    public CarManagementService(
            CarRepository carRepository,
            InsurancePolicyRepository insurancePolicyRepository,
            CarVignetteSelectionRepository carVignetteSelectionRepository,
            CheckScheduleRepository checkScheduleRepository,
            ObligationStateRepository obligationStateRepository
    ) {
        this.carRepository = carRepository;
        this.insurancePolicyRepository = insurancePolicyRepository;
        this.carVignetteSelectionRepository = carVignetteSelectionRepository;
        this.checkScheduleRepository = checkScheduleRepository;
        this.obligationStateRepository = obligationStateRepository;
    }

    @Transactional
    public Car createCar(CreateCarCommand command) {
        Car car = new Car();
        car.setName(command.name());
        car.setLicensePlate(command.licensePlate());
        car.setRegistrationDate(command.registrationDate());
        car.setVin(command.vin());
        car.setActive(true);

        Car savedCar = carRepository.save(car);
        Long carId = savedCar.getId();

        insurancePolicyRepository.save(createDefaultInsurancePolicy(carId, PolicyType.PZP));
        insurancePolicyRepository.save(createDefaultInsurancePolicy(carId, PolicyType.COLLISION));

        Set<CountryCode> vignetteCountries = toCountrySet(command.vignetteCountries());
        for (CountryCode country : vignetteCountries) {
            CarVignetteSelection selection = new CarVignetteSelection();
            selection.setCarId(carId);
            selection.setCountry(country);
            selection.setEnabled(true);
            carVignetteSelectionRepository.save(selection);
        }

        for (CheckType checkType : resolveCheckTypes(vignetteCountries)) {
            checkScheduleRepository.save(createDefaultSchedule(carId, checkType));
        }

        for (ObligationType obligationType : resolveObligationTypes(vignetteCountries)) {
            obligationStateRepository.save(createDefaultObligationState(carId, obligationType));
        }

        return savedCar;
    }

    public Car updateCar(UpdateCarCommand command) {
        Car existing = carRepository.findById(command.id())
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + command.id()));

        if (existing.getVersion() != command.version()) {
            throw new OptimisticLockingFailureException(
                    "Car version mismatch for id " + command.id() + ": expected "
                            + existing.getVersion() + ", got " + command.version()
            );
        }

        existing.setName(command.name());
        existing.setLicensePlate(command.licensePlate());
        existing.setRegistrationDate(command.registrationDate());
        existing.setVin(command.vin());
        existing.setVersion(existing.getVersion() + 1);
        return carRepository.save(existing);
    }

    public Car deactivateCar(Long id) {
        Car existing = carRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + id));
        existing.setActive(false);
        existing.setVersion(existing.getVersion() + 1);
        return carRepository.save(existing);
    }

    public Optional<Car> findById(Long id) {
        return carRepository.findById(id);
    }

    public List<Car> findAllActive() {
        return carRepository.findAllActive();
    }

    public List<Car> findAll() {
        return carRepository.findAll();
    }

    private InsurancePolicy createDefaultInsurancePolicy(Long carId, PolicyType policyType) {
        InsurancePolicy policy = new InsurancePolicy();
        policy.setCarId(carId);
        policy.setPolicyType(policyType);
        policy.setEnabled(true);
        policy.setCheckMode(CheckMode.MANUAL);
        policy.setStatus(ExpiryStatus.UNKNOWN);
        policy.setWarningDaysBefore(INSURANCE_WARNING_DAYS);
        return policy;
    }

    private CheckSchedule createDefaultSchedule(Long carId, CheckType checkType) {
        CheckSchedule schedule = new CheckSchedule();
        schedule.setCarId(carId);
        schedule.setCheckType(checkType);
        schedule.setCronExpression(DEFAULT_CRON);
        schedule.setZoneId(DEFAULT_ZONE_ID);
        schedule.setEnabled(true);
        schedule.setWarningDaysBefore(resolveScheduleWarningDays(checkType));
        return schedule;
    }

    private int resolveScheduleWarningDays(CheckType checkType) {
        return switch (checkType) {
            case PZP_CHECK, COLLISION_INSURANCE_CHECK -> INSURANCE_WARNING_DAYS;
            case STK_CHECK, EK_CHECK -> STK_EK_WARNING_DAYS;
            case VIGNETTE_SK_CHECK, VIGNETTE_CZ_CHECK, VIGNETTE_AT_CHECK -> VIGNETTE_WARNING_DAYS;
            default -> 0;
        };
    }

    private ObligationState createDefaultObligationState(Long carId, ObligationType obligationType) {
        ObligationState state = new ObligationState();
        state.setCarId(carId);
        state.setObligationType(obligationType);
        state.setStatus(ExpiryStatus.UNKNOWN);
        return state;
    }

    private List<CheckType> resolveCheckTypes(Set<CountryCode> vignetteCountries) {
        List<CheckType> checkTypes = new ArrayList<>();
        checkTypes.add(CheckType.PZP_CHECK);
        checkTypes.add(CheckType.COLLISION_INSURANCE_CHECK);
        checkTypes.add(CheckType.STK_CHECK);
        checkTypes.add(CheckType.EK_CHECK);

        if (vignetteCountries.contains(CountryCode.SK)) {
            checkTypes.add(CheckType.VIGNETTE_SK_CHECK);
        }
        if (vignetteCountries.contains(CountryCode.CZ)) {
            checkTypes.add(CheckType.VIGNETTE_CZ_CHECK);
        }
        if (vignetteCountries.contains(CountryCode.AT)) {
            checkTypes.add(CheckType.VIGNETTE_AT_CHECK);
        }
        return checkTypes;
    }

    private Set<CountryCode> toCountrySet(Set<CountryCode> countries) {
        if (countries == null || countries.isEmpty()) {
            return EnumSet.noneOf(CountryCode.class);
        }
        return EnumSet.copyOf(countries);
    }

    private List<ObligationType> resolveObligationTypes(Set<CountryCode> vignetteCountries) {
        List<ObligationType> obligationTypes = new ArrayList<>();
        obligationTypes.add(ObligationType.PZP);
        obligationTypes.add(ObligationType.COLLISION);
        obligationTypes.add(ObligationType.STK);
        obligationTypes.add(ObligationType.EK);

        if (vignetteCountries.contains(CountryCode.SK)) {
            obligationTypes.add(ObligationType.VIGNETTE_SK);
        }
        if (vignetteCountries.contains(CountryCode.CZ)) {
            obligationTypes.add(ObligationType.VIGNETTE_CZ);
        }
        if (vignetteCountries.contains(CountryCode.AT)) {
            obligationTypes.add(ObligationType.VIGNETTE_AT);
        }
        return obligationTypes;
    }
}
