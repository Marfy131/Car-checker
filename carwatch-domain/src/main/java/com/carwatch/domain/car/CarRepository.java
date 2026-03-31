package com.carwatch.domain.car;

import java.util.List;
import java.util.Optional;

public interface CarRepository {

    Car save(Car car);

    Optional<Car> findById(Long id);

    Optional<Car> findByLicensePlate(String licensePlate);

    List<Car> findAllActive();

    List<Car> findAll();
}
