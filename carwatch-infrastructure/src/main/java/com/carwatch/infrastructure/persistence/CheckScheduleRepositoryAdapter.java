package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CheckScheduleRepositoryAdapter implements CheckScheduleRepository {

    private final JdbcCheckScheduleRepository jdbc;

    public CheckScheduleRepositoryAdapter(JdbcCheckScheduleRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CheckSchedule save(CheckSchedule schedule) {
        return jdbc.save(schedule);
    }

    @Override
    public Optional<CheckSchedule> findById(Long id) {
        return jdbc.findById(id);
    }

    @Override
    public List<CheckSchedule> findDueSchedules(LocalDateTime now) {
        return jdbc.findDueSchedules(now);
    }

    @Override
    public List<CheckSchedule> findByCarId(Long carId) {
        return jdbc.findByCarId(carId);
    }

    @Override
    public List<CheckSchedule> findAll() {
        return jdbc.findAll();
    }
}
