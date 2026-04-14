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
import com.carwatch.domain.schedule.CheckTypeMapping;
import com.carwatch.domain.vignette.CarVignetteSelection;
import com.carwatch.domain.vignette.CarVignetteSelectionRepository;
import com.carwatch.domain.vignette.CountryCode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CarManagementService {

    private static final Logger logger = LoggerFactory.getLogger(CarManagementService.class);

    private static final String DEFAULT_CRON = "0 0 6 * * *";
    private static final String DEFAULT_ZONE_ID = "Europe/Bratislava";
    private static final int INSURANCE_WARNING_DAYS = 14;
    private static final int STK_EK_WARNING_DAYS = 30;
    private static final int VIGNETTE_WARNING_DAYS = 7;
    private static final List<CheckType> BASE_CHECK_TYPES = List.of(
            CheckType.PZP_CHECK,
            CheckType.COLLISION_INSURANCE_CHECK,
            CheckType.STK_CHECK,
            CheckType.EK_CHECK
    );

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
        Set<CountryCode> vignetteCountries = toCountrySet(command.vignetteCountries());
        List<CheckType> checkTypes = resolveCheckTypes(vignetteCountries);
        List<ObligationType> obligationTypes = resolveObligationTypes(vignetteCountries);

        logger.info(
                "Creating car registrationDate={} vignetteCountries={} vignetteCount={}",
                command.registrationDate(),
                vignetteCountries,
                vignetteCountries.size()
        );

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

        for (CountryCode country : vignetteCountries) {
            CarVignetteSelection selection = new CarVignetteSelection();
            selection.setCarId(carId);
            selection.setCountry(country);
            selection.setEnabled(true);
            carVignetteSelectionRepository.save(selection);
        }

        for (CheckType checkType : checkTypes) {
            checkScheduleRepository.save(createDefaultSchedule(carId, checkType));
        }

        for (ObligationType obligationType : obligationTypes) {
            obligationStateRepository.save(createDefaultObligationState(carId, obligationType));
        }

        logger.info(
                "Created car carId={} insurancePolicies={} schedules={} obligations={} vignetteSelections={}",
                carId,
                2,
                checkTypes.size(),
                obligationTypes.size(),
                vignetteCountries.size()
        );

        return savedCar;
    }

    @Transactional
    public Car updateCar(UpdateCarCommand command) {
        Car existing = carRepository.findById(command.id())
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + command.id()));

        existing.setName(command.name());
        existing.setLicensePlate(command.licensePlate());
        existing.setRegistrationDate(command.registrationDate());
        existing.setVin(command.vin());
        existing.setVersion(command.version());
        return carRepository.save(existing);
    }

    @Transactional
    public Car deactivateCar(Long id, int version) {
        Car existing = carRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + id));
        existing.setActive(false);
        existing.setVersion(version);
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
            default -> CheckTypeMapping.findVignetteCountry(checkType)
                    .map(countryCode -> VIGNETTE_WARNING_DAYS)
                    .orElse(0);
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
        List<CheckType> checkTypes = new ArrayList<>(BASE_CHECK_TYPES);
        for (CountryCode countryCode : CheckTypeMapping.supportedVignetteCountries()) {
            if (vignetteCountries.contains(countryCode)) {
                CheckTypeMapping.findVignetteCheckType(countryCode).ifPresent(checkTypes::add);
            }
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
        for (CheckType checkType : resolveCheckTypes(vignetteCountries)) {
            CheckTypeMapping.findObligationType(checkType).ifPresent(obligationTypes::add);
        }
        return obligationTypes;
    }
}
