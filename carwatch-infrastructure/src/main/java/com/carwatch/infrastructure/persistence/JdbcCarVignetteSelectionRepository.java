package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.vignette.CarVignetteSelection;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

@NoRepositoryBean
public interface JdbcCarVignetteSelectionRepository extends CrudRepository<CarVignetteSelection, Long> {

    List<CarVignetteSelection> findByCarId(Long carId);

    @Modifying
    @Query("DELETE FROM car_vignette_selection WHERE car_id = :carId")
    void deleteByCarId(@Param("carId") Long carId);
}
