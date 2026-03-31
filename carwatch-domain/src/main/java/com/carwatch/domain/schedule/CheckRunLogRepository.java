package com.carwatch.domain.schedule;

import java.util.List;

public interface CheckRunLogRepository {

    CheckRunLog save(CheckRunLog log);

    List<CheckRunLog> findByScheduleId(Long scheduleId);

    List<CheckRunLog> findByCarId(Long carId);

    List<CheckRunLog> findRecentRuns(int limit);

    List<CheckRunLog> findAll();
}
