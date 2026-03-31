package com.carwatch.application.schedule;

import com.carwatch.application.notification.ReminderNotificationService;
import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import com.carwatch.domain.schedule.VehicleCheckProvider;
import com.carwatch.domain.setting.AppSettingRepository;
import java.util.List;
import java.util.Locale;
public class ScheduledWorkflowProvider implements VehicleCheckProvider {

    private final CheckType checkType;
    private final ReminderNotificationService reminderNotificationService;
    private final AppSettingRepository appSettingRepository;

    public ScheduledWorkflowProvider(
            CheckType checkType,
            ReminderNotificationService reminderNotificationService,
            AppSettingRepository appSettingRepository
    ) {
        this.checkType = checkType;
        this.reminderNotificationService = reminderNotificationService;
        this.appSettingRepository = appSettingRepository;
    }

    @Override
    public CheckType supportedType() {
        return checkType;
    }

    @Override
    public CheckOutcome execute(CheckCommand command) {
        String recipients = reminderNotificationService.resolveRecipientsFromSettings(appSettingRepository.findAll());
        if (checkType == CheckType.DAILY_REMINDER_SCAN) {
            String recipient = firstRecipient(recipients);
            if (recipient.isEmpty()) {
                return new CheckOutcome(RunStatus.NO_DATA, "No recipient configured for reminders", null, null);
            }
            int sent = reminderNotificationService.sendDailyReminders(recipient);
            return new CheckOutcome(RunStatus.SUCCESS, "Daily reminders sent: " + sent, null, "{\"sent\":" + sent + "}");
        }
        if (checkType == CheckType.DAILY_SUMMARY_EMAIL) {
            if (recipients.isBlank()) {
                return new CheckOutcome(RunStatus.NO_DATA, "No recipients configured for summary", null, null);
            }
            int sent = reminderNotificationService.sendDailySummary(recipients, resolveLocale(appSettingRepository.findAll()));
            return new CheckOutcome(RunStatus.SUCCESS, "Daily summaries sent: " + sent, null, "{\"sent\":" + sent + "}");
        }
        return new CheckOutcome(RunStatus.NO_DATA, "Unsupported workflow check", null, null);
    }

    private Locale resolveLocale(List<com.carwatch.domain.setting.AppSetting> settings) {
        for (com.carwatch.domain.setting.AppSetting setting : settings) {
            if ("app.default-locale".equals(setting.getSettingKey())) {
                return Locale.forLanguageTag(setting.getSettingValue());
            }
        }
        return Locale.ENGLISH;
    }

    private String firstRecipient(String recipientsCsv) {
        if (recipientsCsv == null || recipientsCsv.isBlank()) {
            return "";
        }
        return recipientsCsv.split(",")[0].trim();
    }
}
