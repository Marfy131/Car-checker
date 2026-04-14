package com.carwatch.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carwatch.domain.car.Car;
import com.carwatch.domain.car.CarRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CarRepositoryOptimisticLockingIT {

    @Autowired
    private CarRepository carRepository;

    @Test
    void saveRejectsStaleCarVersion() {
        Car saved = carRepository.save(newCar());

        Car firstReaderCopy = carRepository.findById(saved.getId()).orElseThrow();
        Car secondReaderCopy = carRepository.findById(saved.getId()).orElseThrow();

        assertThat(firstReaderCopy.getVersion()).isEqualTo(secondReaderCopy.getVersion());

        int originalVersion = firstReaderCopy.getVersion();
        firstReaderCopy.setName("Updated by first reader");

        Car updated = carRepository.save(firstReaderCopy);

        assertThat(updated.getVersion()).isEqualTo(originalVersion + 1);

        secondReaderCopy.setName("Updated by stale reader");

        assertThatThrownBy(() -> carRepository.save(secondReaderCopy))
                .isInstanceOf(OptimisticLockingFailureException.class);

        Car reloaded = carRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Updated by first reader");
        assertThat(reloaded.getVersion()).isEqualTo(updated.getVersion());
    }

    private Car newCar() {
        LocalDateTime now = LocalDateTime.now();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Car car = new Car();
        car.setName("Optimistic Lock Test " + uniqueSuffix);
        car.setLicensePlate("TT" + uniqueSuffix);
        car.setRegistrationDate(LocalDate.of(2024, 1, 1));
        car.setVin("VIN" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase());
        car.setActive(true);
        car.setCreatedAt(now);
        car.setUpdatedAt(now);
        return car;
    }
}
