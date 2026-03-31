package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.setting.AppSetting;
import com.carwatch.domain.setting.AppSettingRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AppSettingRepositoryAdapter implements AppSettingRepository {

    private final JdbcAppSettingRepository jdbc;

    public AppSettingRepositoryAdapter(JdbcAppSettingRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AppSetting> findByKey(String key) {
        return jdbc.findByKey(key);
    }

    @Override
    public AppSetting save(AppSetting setting) {
        return jdbc.save(setting);
    }

    @Override
    public List<AppSetting> findAll() {
        return jdbc.findAll();
    }
}
