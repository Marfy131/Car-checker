package com.carwatch.domain.vignette;

import java.util.List;

public interface CarVignetteSelectionRepository {

    CarVignetteSelection save(CarVignetteSelection selection);

    List<CarVignetteSelection> findByCarId(Long carId);

    void deleteByCarId(Long carId);

    List<CarVignetteSelection> findAll();
}
