package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.car.Car;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JdbcCarRepository extends CrudRepository<Car, Long> {

    Optional<Car> findByLicensePlate(String licensePlate);

    @Query("SELECT * FROM car WHERE active = 1")
    List<Car> findAllActive();

    @Override
    List<Car> findAll();
}
