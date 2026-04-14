package com.carwatch.web.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.carwatch.application.settings.SettingsService;
import com.carwatch.domain.setting.AppSetting;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SettingsController.class)
@Import(SettingsController.class)
class SettingsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettingsService settingsService;

    @Test
    void getRendersSettingsFormFromServiceSnapshot() throws Exception {
        AppSetting locale = setting("app.default-locale", "en");
        AppSetting recipients = setting("app.email.recipients", "ops@example.com");
        when(settingsService.loadSettings())
                .thenReturn(new SettingsService.SettingsSnapshot("en", "ops@example.com", List.of(locale, recipients)));

        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/form"))
                .andExpect(model().attributeExists("settingsForm"))
                .andExpect(model().attribute("settingsForm", allOf(
                        hasProperty("defaultLocale", is("en")),
                        hasProperty("emailRecipients", is("ops@example.com"))
                )))
                .andExpect(model().attribute("allSettings", List.of(locale, recipients)));
    }

    @Test
    void validPostDelegatesToSettingsServiceAndRedirects() throws Exception {
        mockMvc.perform(post("/settings")
                        .param("defaultLocale", "en")
                        .param("emailRecipients", "ops@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        verify(settingsService).saveSettings("en", "ops@example.com");
    }

    @Test
    void invalidPostReturnsFormAndRepopulatesAllSettings() throws Exception {
        AppSetting locale = setting("app.default-locale", "sk");
        when(settingsService.findAll()).thenReturn(List.of(locale));

        mockMvc.perform(post("/settings")
                        .param("defaultLocale", "")
                        .param("emailRecipients", "ops@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/form"))
                .andExpect(model().attributeHasErrors("settingsForm"))
                .andExpect(model().attributeHasFieldErrors("settingsForm", "defaultLocale"))
                .andExpect(model().attribute("allSettings", List.of(locale)));

        verify(settingsService).findAll();
        verify(settingsService, never()).saveSettings(anyString(), anyString());
    }

    private AppSetting setting(String key, String value) {
        AppSetting setting = new AppSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
