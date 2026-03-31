package com.carwatch.web.controller;

import com.carwatch.application.schedule.ScheduleManagementService;
import com.carwatch.web.form.ScheduleForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/schedules")
public class ScheduleController {

    private final ScheduleManagementService scheduleManagementService;

    public ScheduleController(ScheduleManagementService scheduleManagementService) {
        this.scheduleManagementService = scheduleManagementService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("schedules", scheduleManagementService.findAll());
        return "schedules/list";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        var schedule = scheduleManagementService.findById(id);
        ScheduleForm form = new ScheduleForm();
        form.setId(schedule.getId());
        form.setCronExpression(schedule.getCronExpression());
        form.setWarningDaysBefore(schedule.getWarningDaysBefore());
        form.setEnabled(schedule.isEnabled());
        model.addAttribute("scheduleForm", form);
        return "schedules/form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("scheduleForm") ScheduleForm form,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            return "schedules/form";
        }
        scheduleManagementService.updateSchedule(id, form.getCronExpression(), form.getWarningDaysBefore(), form.isEnabled());
        return "redirect:/schedules";
    }

    @PostMapping("/{id}/run-now")
    public String runNow(@PathVariable Long id) {
        scheduleManagementService.runNow(id);
        return "redirect:/schedules";
    }
}
