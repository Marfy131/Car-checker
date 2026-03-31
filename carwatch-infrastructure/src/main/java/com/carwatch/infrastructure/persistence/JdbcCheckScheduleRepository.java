package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.schedule.CheckSchedule;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JdbcCheckScheduleRepository extends CrudRepository<CheckSchedule, Long> {

    @Query("SELECT * FROM check_schedule WHERE enabled = 1 AND next_run_at <= :now AND (lock_until IS NULL OR lock_until < :now)")
    List<CheckSchedule> findDueSchedules(@Param("now") LocalDateTime now);

    List<CheckSchedule> findByCarId(Long carId);

    @Override
    List<CheckSchedule> findAll();
}
