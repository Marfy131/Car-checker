package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.notification.NotificationLog;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JdbcNotificationLogRepository extends CrudRepository<NotificationLog, Long> {

    @Query("SELECT * FROM notification_log ORDER BY sent_at DESC LIMIT :limit")
    List<NotificationLog> findRecent(@Param("limit") int limit);

    @Query("""
            SELECT COUNT(*) > 0
            FROM notification_log
            WHERE car_id = :carId
              AND obligation_type = :obligationType
              AND notification_type = :type
              AND dedupe_date = :date
            """)
    boolean existsByCarIdAndObligationTypeAndTypeAndDate(
            @Param("carId") Long carId,
            @Param("obligationType") String obligationType,
            @Param("type") String type,
            @Param("date") String date
    );

    @Override
    List<NotificationLog> findAll();
}
