package com.carwatch.application.settings;

import com.carwatch.domain.setting.AppSetting;
import com.carwatch.domain.setting.AppSettingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private static final String DEFAULT_LOCALE_KEY = "app.default-locale";
    private static final String EMAIL_RECIPIENTS_KEY = "app.email.recipients";

    private final AppSettingRepository appSettingRepository;
    private final Clock clock;

    public SettingsService(AppSettingRepository appSettingRepository, Clock clock) {
        this.appSettingRepository = appSettingRepository;
        this.clock = clock;
    }

    public SettingsSnapshot loadSettings() {
        List<AppSetting> allSettings = appSettingRepository.findAll();
        Map<String, String> map = new LinkedHashMap<>();
        for (AppSetting setting : allSettings) {
            map.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return new SettingsSnapshot(
            map.getOrDefault(DEFAULT_LOCALE_KEY, "sk"),
            map.getOrDefault(EMAIL_RECIPIENTS_KEY, ""),
            allSettings
        );
    }

    public List<AppSetting> findAll() {
        return appSettingRepository.findAll();
    }

    @Transactional
    public void saveSettings(String defaultLocale, String emailRecipients) {
        saveSetting(DEFAULT_LOCALE_KEY, defaultLocale);
        saveSetting(EMAIL_RECIPIENTS_KEY, emailRecipients);
    }

    private void saveSetting(String key, String value) {
        AppSetting setting = appSettingRepository.findByKey(key).orElseGet(AppSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(value == null ? "" : value.trim());
        setting.setUpdatedAt(LocalDateTime.now(clock));
        appSettingRepository.save(setting);
    }

    public record SettingsSnapshot(String defaultLocale, String emailRecipients, List<AppSetting> allSettings) {
    }
}
