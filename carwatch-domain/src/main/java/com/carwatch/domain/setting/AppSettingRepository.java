package com.carwatch.domain.setting;

import java.util.List;
import java.util.Optional;

public interface AppSettingRepository {

    Optional<AppSetting> findByKey(String key);

    AppSetting save(AppSetting setting);

    List<AppSetting> findAll();
}
