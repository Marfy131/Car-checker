package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.setting.AppSetting;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JdbcAppSettingRepository extends CrudRepository<AppSetting, Long> {

    @Query("SELECT * FROM app_setting WHERE setting_key = :key")
    Optional<AppSetting> findByKey(@Param("key") String key);

    @Override
    List<AppSetting> findAll();
}
