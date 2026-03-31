package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.schedule.CheckRunLog;
import com.carwatch.domain.schedule.CheckRunLogRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CheckRunLogRepositoryAdapter implements CheckRunLogRepository {

    private final JdbcCheckRunLogRepository jdbc;

    public CheckRunLogRepositoryAdapter(JdbcCheckRunLogRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CheckRunLog save(CheckRunLog log) {
        return jdbc.save(log);
    }

    @Override
    public List<CheckRunLog> findByScheduleId(Long scheduleId) {
        return jdbc.findByScheduleId(scheduleId);
    }

    @Override
    public List<CheckRunLog> findByCarId(Long carId) {
        return jdbc.findByCarId(carId);
    }

    @Override
    public List<CheckRunLog> findRecentRuns(int limit) {
        return jdbc.findRecentRuns(limit);
    }

    @Override
    public List<CheckRunLog> findAll() {
        return jdbc.findAll();
    }
}
