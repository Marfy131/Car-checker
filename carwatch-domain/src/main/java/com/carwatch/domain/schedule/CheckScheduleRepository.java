package com.carwatch.domain.schedule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckScheduleRepository {

    CheckSchedule save(CheckSchedule schedule);

    Optional<CheckSchedule> findById(Long id);

    List<CheckSchedule> findDueSchedules(LocalDateTime now);

    List<CheckSchedule> findByCarId(Long carId);

    List<CheckSchedule> findAll();
}
