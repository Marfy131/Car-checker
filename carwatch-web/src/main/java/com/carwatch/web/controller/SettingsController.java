package com.carwatch.web.controller;

import com.carwatch.application.settings.SettingsService;
import com.carwatch.web.form.SettingsForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        SettingsService.SettingsSnapshot snapshot = settingsService.loadSettings();
        SettingsForm form = new SettingsForm();
        form.setDefaultLocale(snapshot.defaultLocale());
        form.setEmailRecipients(snapshot.emailRecipients());
        model.addAttribute("settingsForm", form);
        model.addAttribute("allSettings", snapshot.allSettings());
        return "settings/form";
    }

    @PostMapping("/settings")
    public String saveSettings(@Valid @ModelAttribute("settingsForm") SettingsForm form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("allSettings", settingsService.findAll());
            return "settings/form";
        }
        settingsService.saveSettings(form.getDefaultLocale(), form.getEmailRecipients());
        return "redirect:/settings";
    }
}
