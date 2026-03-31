package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.schedule.CheckRunLog;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JdbcCheckRunLogRepository extends CrudRepository<CheckRunLog, Long> {

    List<CheckRunLog> findByScheduleId(Long scheduleId);

    List<CheckRunLog> findByCarId(Long carId);

    @Query("SELECT * FROM check_run_log ORDER BY started_at DESC LIMIT :limit")
    List<CheckRunLog> findRecentRuns(@Param("limit") int limit);

    @Override
    List<CheckRunLog> findAll();
}
