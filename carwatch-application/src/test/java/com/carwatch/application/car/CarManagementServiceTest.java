package com.carwatch.application.car;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class CarManagementServiceTest {

    @Mock
    private CarRepository carRepository;
    @Mock
    private InsurancePolicyRepository insurancePolicyRepository;
    @Mock
    private CarVignetteSelectionRepository carVignetteSelectionRepository;
    @Mock
    private CheckScheduleRepository checkScheduleRepository;
    @Mock
    private ObligationStateRepository obligationStateRepository;

    @Captor
    private ArgumentCaptor<InsurancePolicy> insurancePolicyCaptor;
    @Captor
    private ArgumentCaptor<CheckSchedule> checkScheduleCaptor;
    @Captor
    private ArgumentCaptor<ObligationState> obligationStateCaptor;
    @Captor
    private ArgumentCaptor<CarVignetteSelection> vignetteSelectionCaptor;
    @Captor
    private ArgumentCaptor<Car> carCaptor;

    private CarManagementService service;

    @BeforeEach
    void setUp() {
        service = new CarManagementService(
                carRepository,
                insurancePolicyRepository,
                carVignetteSelectionRepository,
                checkScheduleRepository,
                obligationStateRepository,
                Clock.systemDefaultZone()
        );
    }

    @Test
    void testCreateCar_createsCarAndInsurancePoliciesAndSchedules() {
        stubCreateFlow();
        when(carVignetteSelectionRepository.save(any(CarVignetteSelection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCarCommand command = new CreateCarCommand(
                "Family Car",
                "BA123CD",
                LocalDate.of(2022, 3, 15),
                "VIN123456789",
                EnumSet.of(CountryCode.SK, CountryCode.CZ)
        );

        Car created = service.createCar(command);

        assertNotNull(created);
        assertEquals(100L, created.getId());

        verify(carRepository).save(any(Car.class));
        verify(insurancePolicyRepository, times(2)).save(insurancePolicyCaptor.capture());
        verify(checkScheduleRepository, times(6)).save(checkScheduleCaptor.capture());
        verify(obligationStateRepository, times(6)).save(obligationStateCaptor.capture());
        verify(carVignetteSelectionRepository, times(2)).save(vignetteSelectionCaptor.capture());

        Set<PolicyType> policyTypes = insurancePolicyCaptor.getAllValues().stream()
                .map(InsurancePolicy::getPolicyType)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(PolicyType.PZP, PolicyType.COLLISION), policyTypes);

        Set<CheckType> checkTypes = checkScheduleCaptor.getAllValues().stream()
                .map(CheckSchedule::getCheckType)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(checkTypes.contains(CheckType.PZP_CHECK));
        assertTrue(checkTypes.contains(CheckType.COLLISION_INSURANCE_CHECK));
        assertTrue(checkTypes.contains(CheckType.STK_CHECK));
        assertTrue(checkTypes.contains(CheckType.EK_CHECK));
        assertTrue(checkTypes.contains(CheckType.VIGNETTE_SK_CHECK));
        assertTrue(checkTypes.contains(CheckType.VIGNETTE_CZ_CHECK));

        Set<ObligationType> obligationTypes = obligationStateCaptor.getAllValues().stream()
                .map(ObligationState::getObligationType)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(obligationTypes.contains(ObligationType.PZP));
        assertTrue(obligationTypes.contains(ObligationType.COLLISION));
        assertTrue(obligationTypes.contains(ObligationType.STK));
        assertTrue(obligationTypes.contains(ObligationType.EK));
        assertTrue(obligationTypes.contains(ObligationType.VIGNETTE_SK));
        assertTrue(obligationTypes.contains(ObligationType.VIGNETTE_CZ));

        Set<CountryCode> selectedCountries = vignetteSelectionCaptor.getAllValues().stream()
                .map(CarVignetteSelection::getCountry)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(CountryCode.SK, CountryCode.CZ), selectedCountries);
    }

    @Test
    void testCreateCar_withAllVignettes() {
        stubCreateFlow();
        when(carVignetteSelectionRepository.save(any(CarVignetteSelection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCarCommand command = new CreateCarCommand(
                "Trip Car",
                "TT111AA",
                LocalDate.of(2023, 1, 10),
                "VINALL123",
                EnumSet.of(CountryCode.SK, CountryCode.CZ, CountryCode.AT)
        );

        service.createCar(command);

        verify(checkScheduleRepository, times(7)).save(any(CheckSchedule.class));
        verify(obligationStateRepository, times(7)).save(any(ObligationState.class));
    }

    @Test
    void testCreateCar_withNoVignettes() {
        stubCreateFlow();

        CreateCarCommand command = new CreateCarCommand(
                "City Car",
                "NR999XY",
                LocalDate.of(2021, 8, 5),
                "VINNONE123",
                Set.of()
        );

        service.createCar(command);

        verify(checkScheduleRepository, times(4)).save(any(CheckSchedule.class));
        verify(obligationStateRepository, times(4)).save(any(ObligationState.class));
        verify(carVignetteSelectionRepository, times(0)).save(any(CarVignetteSelection.class));
    }

    @Test
    void testCreateCar_withNullVignetteCountries_treatsItAsNoVignettes() {
        stubCreateFlow();

        CreateCarCommand command = new CreateCarCommand(
                "Null Vignette Car",
                "BA000NV",
                LocalDate.of(2020, 6, 1),
                "VINNULL123",
                null
        );

        service.createCar(command);

        verify(checkScheduleRepository, times(4)).save(checkScheduleCaptor.capture());
        verify(obligationStateRepository, times(4)).save(obligationStateCaptor.capture());
        verify(carVignetteSelectionRepository, times(0)).save(any(CarVignetteSelection.class));

        Set<CheckType> checkTypes = checkScheduleCaptor.getAllValues().stream()
                .map(CheckSchedule::getCheckType)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                CheckType.PZP_CHECK,
                CheckType.COLLISION_INSURANCE_CHECK,
                CheckType.STK_CHECK,
                CheckType.EK_CHECK
        ), checkTypes);

        Set<ObligationType> obligationTypes = obligationStateCaptor.getAllValues().stream()
                .map(ObligationState::getObligationType)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                ObligationType.PZP,
                ObligationType.COLLISION,
                ObligationType.STK,
                ObligationType.EK
        ), obligationTypes);
    }

    @Test
    void testCreateCar_seedsDefaultPolicyScheduleAndObligationDefaults() {
        stubCreateFlow();
        when(carVignetteSelectionRepository.save(any(CarVignetteSelection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCarCommand command = new CreateCarCommand(
                "Defaults Car",
                "TT222DF",
                LocalDate.of(2024, 1, 10),
                "VINDEFAULT123",
                EnumSet.of(CountryCode.SK, CountryCode.AT)
        );

        service.createCar(command);

        verify(insurancePolicyRepository, times(2)).save(insurancePolicyCaptor.capture());
        verify(checkScheduleRepository, times(6)).save(checkScheduleCaptor.capture());
        verify(obligationStateRepository, times(6)).save(obligationStateCaptor.capture());

        for (InsurancePolicy policy : insurancePolicyCaptor.getAllValues()) {
            assertEquals(100L, policy.getCarId());
            assertTrue(policy.isEnabled());
            assertEquals(CheckMode.MANUAL, policy.getCheckMode());
            assertEquals(ExpiryStatus.UNKNOWN, policy.getStatus());
            assertEquals(14, policy.getWarningDaysBefore());
        }

        Map<CheckType, Integer> warningDaysByCheckType = new EnumMap<>(CheckType.class);
        for (CheckSchedule schedule : checkScheduleCaptor.getAllValues()) {
            assertEquals(100L, schedule.getCarId());
            assertEquals("0 0 6 * * *", schedule.getCronExpression());
            assertEquals("Europe/Bratislava", schedule.getZoneId());
            assertTrue(schedule.isEnabled());
            warningDaysByCheckType.put(schedule.getCheckType(), schedule.getWarningDaysBefore());
        }
        assertEquals(14, warningDaysByCheckType.get(CheckType.PZP_CHECK));
        assertEquals(14, warningDaysByCheckType.get(CheckType.COLLISION_INSURANCE_CHECK));
        assertEquals(30, warningDaysByCheckType.get(CheckType.STK_CHECK));
        assertEquals(30, warningDaysByCheckType.get(CheckType.EK_CHECK));
        assertEquals(7, warningDaysByCheckType.get(CheckType.VIGNETTE_SK_CHECK));
        assertEquals(7, warningDaysByCheckType.get(CheckType.VIGNETTE_AT_CHECK));

        for (ObligationState obligationState : obligationStateCaptor.getAllValues()) {
            assertEquals(100L, obligationState.getCarId());
            assertEquals(ExpiryStatus.UNKNOWN, obligationState.getStatus());
        }
    }

    @Test
    void testCreateCar_withAustriaVignette_seedsOnlyMatchingAustriaArtifacts() {
        stubCreateFlow();
        when(carVignetteSelectionRepository.save(any(CarVignetteSelection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateCarCommand command = new CreateCarCommand(
                "Austria Car",
                "AT111AA",
                LocalDate.of(2023, 11, 3),
                "VINAT123",
                EnumSet.of(CountryCode.AT)
        );

        service.createCar(command);

        verify(checkScheduleRepository, times(5)).save(checkScheduleCaptor.capture());
        verify(obligationStateRepository, times(5)).save(obligationStateCaptor.capture());
        verify(carVignetteSelectionRepository).save(vignetteSelectionCaptor.capture());

        Set<CheckType> checkTypes = checkScheduleCaptor.getAllValues().stream()
                .map(CheckSchedule::getCheckType)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(checkTypes.contains(CheckType.VIGNETTE_AT_CHECK));
        assertFalse(checkTypes.contains(CheckType.VIGNETTE_SK_CHECK));
        assertFalse(checkTypes.contains(CheckType.VIGNETTE_CZ_CHECK));

        Set<ObligationType> obligationTypes = obligationStateCaptor.getAllValues().stream()
                .map(ObligationState::getObligationType)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(obligationTypes.contains(ObligationType.VIGNETTE_AT));
        assertFalse(obligationTypes.contains(ObligationType.VIGNETTE_SK));
        assertFalse(obligationTypes.contains(ObligationType.VIGNETTE_CZ));

        CarVignetteSelection selection = vignetteSelectionCaptor.getValue();
        assertEquals(100L, selection.getCarId());
        assertEquals(CountryCode.AT, selection.getCountry());
        assertTrue(selection.isEnabled());
    }

    @Test
    void testDeactivateCar_usesSubmittedVersionForOptimisticLocking() {
        Car existing = new Car();
        existing.setId(33L);
        existing.setActive(true);
        existing.setVersion(4);
        when(carRepository.findById(33L)).thenReturn(Optional.of(existing));
        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Car result = service.deactivateCar(33L, 3);

        assertFalse(result.isActive());
        verify(carRepository).save(carCaptor.capture());
        assertFalse(carCaptor.getValue().isActive());
        assertEquals(3, carCaptor.getValue().getVersion());
    }

    @Test
    void testDeactivateCar_propagatesOptimisticLockingFailureExceptionFromRepository() {
        Car existing = new Car();
        existing.setId(34L);
        existing.setActive(true);
        existing.setVersion(4);
        when(carRepository.findById(34L)).thenReturn(Optional.of(existing));
        when(carRepository.save(any(Car.class)))
                .thenThrow(new OptimisticLockingFailureException("stale version"));

        assertThrows(OptimisticLockingFailureException.class, () -> service.deactivateCar(34L, 3));
    }

    @Test
    void testUpdateCar_usesCommandVersionWithoutManualIncrement() {
        Car existing = new Car();
        existing.setId(44L);
        existing.setName("Old");
        existing.setLicensePlate("OLD123");
        existing.setRegistrationDate(LocalDate.of(2020, 1, 1));
        existing.setVin("OLDVIN");
        existing.setVersion(7);
        when(carRepository.findById(44L)).thenReturn(Optional.of(existing));
        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCarCommand command = new UpdateCarCommand(
                44L,
                "New",
                "NEW123",
                LocalDate.of(2024, 2, 3),
                "NEWVIN",
                7
        );

        Car result = service.updateCar(command);

        assertEquals("New", result.getName());
        assertEquals("NEW123", result.getLicensePlate());
        assertEquals(LocalDate.of(2024, 2, 3), result.getRegistrationDate());
        assertEquals("NEWVIN", result.getVin());
        assertEquals(7, result.getVersion());
        verify(carRepository).save(carCaptor.capture());
        assertEquals(7, carCaptor.getValue().getVersion());
    }

    @Test
    void testUpdateCar_propagatesOptimisticLockingFailureExceptionFromRepository() {
        Car existing = new Car();
        existing.setId(45L);
        existing.setVersion(5);
        when(carRepository.findById(45L)).thenReturn(Optional.of(existing));
        when(carRepository.save(any(Car.class)))
                .thenThrow(new OptimisticLockingFailureException("stale version"));

        UpdateCarCommand command = new UpdateCarCommand(
                45L,
                "New",
                "NEW123",
                LocalDate.of(2024, 2, 3),
                "NEWVIN",
                4
        );

        assertThrows(OptimisticLockingFailureException.class, () -> service.updateCar(command));
    }

    @Test
    void testFindAllActive() {
        List<Car> activeCars = List.of(new Car(), new Car());
        when(carRepository.findAllActive()).thenReturn(activeCars);

        List<Car> result = service.findAllActive();

        assertEquals(activeCars, result);
        verify(carRepository).findAllActive();
    }

    private void stubCreateFlow() {
        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> {
            Car car = invocation.getArgument(0);
            if (car.getId() == null) {
                car.setId(100L);
            }
            return car;
        });
        when(insurancePolicyRepository.save(any(InsurancePolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(checkScheduleRepository.save(any(CheckSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(obligationStateRepository.save(any(ObligationState.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
