package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.car.Car;
import com.carwatch.domain.car.CarRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CarRepositoryAdapter implements CarRepository {

    private final JdbcCarRepository jdbc;

    public CarRepositoryAdapter(JdbcCarRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Car save(Car car) {
        return jdbc.save(car);
    }

    @Override
    public Optional<Car> findById(Long id) {
        return jdbc.findById(id);
    }

    @Override
    public Optional<Car> findByLicensePlate(String licensePlate) {
        return jdbc.findByLicensePlate(licensePlate);
    }

    @Override
    public List<Car> findAllActive() {
        return jdbc.findAllActive();
    }

    @Override
    public List<Car> findAll() {
        return jdbc.findAll();
    }
}
