package com.carwatch.web.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ScheduleForm {

    private Long id;

    @NotBlank(message = "{validation.required}")
    @Size(max = 120)
    private String cronExpression;

    @Min(0)
    private int warningDaysBefore;

    private boolean enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public int getWarningDaysBefore() {
        return warningDaysBefore;
    }

    public void setWarningDaysBefore(int warningDaysBefore) {
        this.warningDaysBefore = warningDaysBefore;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
