package com.carwatch.web.controller;

import com.carwatch.domain.setting.AppSetting;
import com.carwatch.domain.setting.AppSettingRepository;
import com.carwatch.web.form.SettingsForm;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class SettingsController {

    private final AppSettingRepository appSettingRepository;

    public SettingsController(AppSettingRepository appSettingRepository) {
        this.appSettingRepository = appSettingRepository;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        SettingsForm form = new SettingsForm();
        Map<String, String> map = settingsMap();
        form.setDefaultLocale(map.getOrDefault("app.default-locale", "sk"));
        form.setEmailRecipients(map.getOrDefault("app.email.recipients", ""));
        model.addAttribute("settingsForm", form);
        model.addAttribute("allSettings", appSettingRepository.findAll());
        return "settings/form";
    }

    @PostMapping("/settings")
    public String saveSettings(@Valid @ModelAttribute("settingsForm") SettingsForm form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("allSettings", appSettingRepository.findAll());
            return "settings/form";
        }
        saveSetting("app.default-locale", form.getDefaultLocale());
        saveSetting("app.email.recipients", form.getEmailRecipients());
        return "redirect:/settings";
    }

    private void saveSetting(String key, String value) {
        AppSetting setting = appSettingRepository.findByKey(key).orElseGet(AppSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(value == null ? "" : value.trim());
        setting.setUpdatedAt(LocalDateTime.now());
        appSettingRepository.save(setting);
    }

    private Map<String, String> settingsMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (AppSetting setting : appSettingRepository.findAll()) {
            result.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return result;
    }
}
