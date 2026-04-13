package com.carwatch.application.schedule;

import com.carwatch.application.notification.ReminderNotificationService;
import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import com.carwatch.domain.schedule.VehicleCheckProvider;
import com.carwatch.domain.setting.AppSetting;
import com.carwatch.domain.setting.AppSettingRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class ScheduledWorkflowProvider implements VehicleCheckProvider {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledWorkflowProvider.class);

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
        String recipients = appSettingRepository.findByKey("app.email.recipients")
                .map(AppSetting::getSettingValue)
                .orElse("");

        if (checkType == CheckType.DAILY_REMINDER_SCAN) {
            String recipient = firstRecipient(recipients);
            if (recipient.isEmpty()) {
                logger.warn("Skipping daily reminders: No recipients configured in app.email.recipients");
                return new CheckOutcome(RunStatus.NO_DATA, "No recipient configured for reminders", null, null);
            }
            int sent = reminderNotificationService.sendDailyReminders(recipient);
            return new CheckOutcome(RunStatus.SUCCESS, "Daily reminders sent: " + sent, null, "{\"sent\":" + sent + "}");
        }
        if (checkType == CheckType.DAILY_SUMMARY_EMAIL) {
            if (recipients.isBlank()) {
                logger.warn("Skipping daily summary: No recipients configured in app.email.recipients");
                return new CheckOutcome(RunStatus.NO_DATA, "No recipients configured for summary", null, null);
            }
            
            Locale locale = appSettingRepository.findByKey("app.default-locale")
                    .map(setting -> Locale.forLanguageTag(setting.getSettingValue()))
                    .orElse(Locale.ENGLISH);

            int sent = reminderNotificationService.sendDailySummary(recipients, locale);
            return new CheckOutcome(RunStatus.SUCCESS, "Daily summaries sent: " + sent, null, "{\"sent\":" + sent + "}");
        }
        return new CheckOutcome(RunStatus.NO_DATA, "Unsupported workflow check", null, null);
    }

    private String firstRecipient(String recipientsCsv) {
        if (recipientsCsv == null || recipientsCsv.isBlank()) {
            return "";
        }
        return recipientsCsv.split(",")[0].trim();
    }
}
