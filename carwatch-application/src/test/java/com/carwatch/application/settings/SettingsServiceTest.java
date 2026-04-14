package com.carwatch.application.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carwatch.domain.setting.AppSetting;
import com.carwatch.domain.setting.AppSettingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsServiceTest {

    private AppSettingRepository appSettingRepository;
    private SettingsService settingsService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        appSettingRepository = mock(AppSettingRepository.class);
        clock = Clock.fixed(Instant.parse("2026-04-13T09:15:00Z"), ZoneOffset.UTC);
        settingsService = new SettingsService(appSettingRepository, clock);
    }

    @Test
    void loadSettingsUsesStoredValuesAndReturnsAllSettings() {
        AppSetting locale = setting("app.default-locale", "en");
        AppSetting recipients = setting("app.email.recipients", "one@example.com");
        when(appSettingRepository.findAll()).thenReturn(List.of(locale, recipients));

        SettingsService.SettingsSnapshot snapshot = settingsService.loadSettings();

        assertThat(snapshot.defaultLocale()).isEqualTo("en");
        assertThat(snapshot.emailRecipients()).isEqualTo("one@example.com");
        assertThat(snapshot.allSettings()).containsExactly(locale, recipients);
    }

    @Test
    void loadSettingsFallsBackToCurrentDefaultsWhenMissing() {
        when(appSettingRepository.findAll()).thenReturn(List.of());

        SettingsService.SettingsSnapshot snapshot = settingsService.loadSettings();

        assertThat(snapshot.defaultLocale()).isEqualTo("sk");
        assertThat(snapshot.emailRecipients()).isEqualTo("");
        assertThat(snapshot.allSettings()).isEmpty();
    }

    @Test
    void saveSettingsTrimsValuesAndUpdatesTimestampViaClock() {
        AppSetting existingLocale = setting("app.default-locale", "sk");
        when(appSettingRepository.findByKey("app.default-locale")).thenReturn(Optional.of(existingLocale));
        when(appSettingRepository.findByKey("app.email.recipients")).thenReturn(Optional.empty());
        List<AppSetting> savedSettings = new ArrayList<>();
        when(appSettingRepository.save(any(AppSetting.class))).thenAnswer(invocation -> {
            AppSetting setting = invocation.getArgument(0);
            savedSettings.add(setting);
            return setting;
        });

        settingsService.saveSettings(" en ", " first@example.com, second@example.com ");

        assertThat(savedSettings).hasSize(2);
        assertThat(savedSettings.get(0)).isSameAs(existingLocale);
        assertThat(savedSettings.get(0).getSettingKey()).isEqualTo("app.default-locale");
        assertThat(savedSettings.get(0).getSettingValue()).isEqualTo("en");
        assertThat(savedSettings.get(0).getUpdatedAt()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        assertThat(savedSettings.get(1).getSettingKey()).isEqualTo("app.email.recipients");
        assertThat(savedSettings.get(1).getSettingValue()).isEqualTo("first@example.com, second@example.com");
        assertThat(savedSettings.get(1).getUpdatedAt()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    private AppSetting setting(String key, String value) {
        AppSetting setting = new AppSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
