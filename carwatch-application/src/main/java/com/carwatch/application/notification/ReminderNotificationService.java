package com.carwatch.application.notification;

import com.carwatch.domain.car.Car;
import com.carwatch.domain.car.CarRepository;
import com.carwatch.domain.notification.NotificationChannel;
import com.carwatch.domain.notification.NotificationLog;
import com.carwatch.domain.notification.NotificationLogRepository;
import com.carwatch.domain.notification.NotificationStatus;
import com.carwatch.domain.notification.NotificationType;
import com.carwatch.domain.obligation.ExpiryStatus;
import com.carwatch.domain.obligation.ObligationState;
import com.carwatch.domain.obligation.ObligationStateRepository;
import com.carwatch.domain.obligation.ObligationType;
import com.carwatch.domain.schedule.CheckRunLog;
import com.carwatch.domain.schedule.CheckRunLogRepository;
import com.carwatch.domain.vignette.CarVignetteSelection;
import com.carwatch.domain.vignette.CarVignetteSelectionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderNotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SETTINGS_RECIPIENTS = "app.email.recipients";

    private final ObligationStateRepository obligationStateRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final CarRepository carRepository;
    private final EmailSender emailSender;
    private final DailySummaryTemplateRenderer dailySummaryTemplateRenderer;
    private final CheckRunLogRepository checkRunLogRepository;
    private final CarVignetteSelectionRepository carVignetteSelectionRepository;
    private final Clock clock;

    @Autowired
    public ReminderNotificationService(
            ObligationStateRepository obligationStateRepository,
            NotificationLogRepository notificationLogRepository,
            CarRepository carRepository,
            EmailSender emailSender,
            DailySummaryTemplateRenderer dailySummaryTemplateRenderer,
            CheckRunLogRepository checkRunLogRepository,
            CarVignetteSelectionRepository carVignetteSelectionRepository
    ) {
        this(
                obligationStateRepository,
                notificationLogRepository,
                carRepository,
                emailSender,
                dailySummaryTemplateRenderer,
                checkRunLogRepository,
                carVignetteSelectionRepository,
                Clock.systemDefaultZone()
        );
    }

    ReminderNotificationService(
            ObligationStateRepository obligationStateRepository,
            NotificationLogRepository notificationLogRepository,
            CarRepository carRepository,
            EmailSender emailSender,
            DailySummaryTemplateRenderer dailySummaryTemplateRenderer,
            CheckRunLogRepository checkRunLogRepository,
            CarVignetteSelectionRepository carVignetteSelectionRepository,
            Clock clock
    ) {
        this.obligationStateRepository = obligationStateRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.carRepository = carRepository;
        this.emailSender = emailSender;
        this.dailySummaryTemplateRenderer = dailySummaryTemplateRenderer;
        this.checkRunLogRepository = checkRunLogRepository;
        this.carVignetteSelectionRepository = carVignetteSelectionRepository;
        this.clock = clock;
    }

    @Transactional
    public int sendImmediateReminderForState(ObligationState state, String recipient) {
        if (state.getCarId() == null || state.getStatus() == null || state.getStatus() == ExpiryStatus.VALID) {
            return 0;
        }
        NotificationType type = state.getStatus() == ExpiryStatus.EXPIRED
                ? NotificationType.EXPIRED
                : NotificationType.EXPIRY_WARNING;
        LocalDate today = LocalDate.now(clock);
        if (notificationLogRepository.existsByCarIdAndObligationTypeAndTypeAndDate(
                state.getCarId(),
                state.getObligationType(),
                type,
                today
        )) {
            return 0;
        }

        Car car = carRepository.findById(state.getCarId()).orElse(null);
        String carLabel = car == null ? "Car " + state.getCarId() : car.getName() + " (" + car.getLicensePlate() + ")";
        String obligationLabel = humanObligation(state.getObligationType(), Locale.ENGLISH);
        String expiry = state.getExpiryDate() == null ? "unknown" : state.getExpiryDate().toString();
        String subject = type == NotificationType.EXPIRED
                ? "Expired obligation: " + obligationLabel + " - " + carLabel
                : "Expiry warning: " + obligationLabel + " - " + carLabel;
        String body = "<p>" + obligationLabel + " for " + carLabel + " has status " + state.getStatus() + ".</p>"
                + "<p>Expiry date: " + expiry + "</p>";

        NotificationLog log = createLog(
                state.getCarId(),
                null,
                state.getObligationType(),
                type,
                subject,
                recipient,
                today,
                null
        );

        SendResult sendResult = emailSender.send(new EmailMessage(recipient, subject, body));
        updateLogFromSendResult(log, sendResult);
        notificationLogRepository.save(log);
        return sendResult.success() ? 1 : 0;
    }

    @Transactional
    public int sendDailyReminders(String recipient) {
        int sent = 0;
        List<ObligationState> states = obligationStateRepository.findAll();
        states.sort(Comparator.comparing(ObligationState::getCarId, Comparator.nullsLast(Long::compareTo)));
        for (ObligationState state : states) {
            if (state.getStatus() == ExpiryStatus.EXPIRING || state.getStatus() == ExpiryStatus.EXPIRED) {
                sent += sendImmediateReminderForState(state, recipient);
            }
        }
        return sent;
    }

    @Transactional
    public int sendDailySummary(String recipientsCsv, Locale locale) {
        List<String> recipients = parseRecipients(recipientsCsv);
        if (recipients.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now(clock);
        String generatedAt = LocalDateTime.now(clock).format(DATE_TIME_FORMAT);
        DailySummaryModel summaryModel = buildSummaryModel(generatedAt, locale);
        String subject = locale.getLanguage().equals("sk")
                ? "Denný prehľad vozidiel - " + today
                : "Daily vehicle summary - " + today;
        String htmlBody = dailySummaryTemplateRenderer.render(summaryModel, locale);

        int sent = 0;
        for (String recipient : recipients) {
            NotificationLog log = createLog(
                    null,
                    null,
                    null,
                    NotificationType.DAILY_SUMMARY,
                    subject,
                    recipient,
                    today,
                    null
            );
            SendResult sendResult = emailSender.send(new EmailMessage(recipient, subject, htmlBody));
            updateLogFromSendResult(log, sendResult);
            notificationLogRepository.save(log);
            if (sendResult.success()) {
                sent++;
            }
        }
        return sent;
    }

    public String resolveRecipientsFromSettings(List<com.carwatch.domain.setting.AppSetting> settings) {
        for (com.carwatch.domain.setting.AppSetting setting : settings) {
            if (SETTINGS_RECIPIENTS.equals(setting.getSettingKey())) {
                return setting.getSettingValue();
            }
        }
        return "";
    }

    private DailySummaryModel buildSummaryModel(String generatedAt, Locale locale) {
        List<Car> cars = carRepository.findAll();
        List<ObligationState> states = obligationStateRepository.findAll();
        Map<Long, Map<ObligationType, ObligationState>> statesByCar = new HashMap<>();
        for (ObligationState state : states) {
            if (state.getCarId() == null) {
                continue;
            }
            statesByCar.computeIfAbsent(state.getCarId(), key -> new HashMap<>()).put(state.getObligationType(), state);
        }

        Map<Long, Set<String>> enabledVignettes = new HashMap<>();
        for (CarVignetteSelection selection : carVignetteSelectionRepository.findAll()) {
            if (selection.isEnabled()) {
                enabledVignettes.computeIfAbsent(selection.getCarId(), key -> new HashSet<>()).add(selection.getCountry().name());
            }
        }

        List<DailySummaryModel.CarSummaryLine> carLines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Car car : cars) {
            Map<ObligationType, ObligationState> carStates = statesByCar.getOrDefault(car.getId(), Map.of());
            Set<String> vignetteCountries = enabledVignettes.getOrDefault(car.getId(), Set.of());
            DailySummaryModel.CarSummaryLine line = new DailySummaryModel.CarSummaryLine(
                    car.getName(),
                    car.getLicensePlate(),
                    displayDate(carStates.get(ObligationType.PZP)),
                    displayStatus(carStates.get(ObligationType.PZP)),
                    displayDate(carStates.get(ObligationType.COLLISION)),
                    displayStatus(carStates.get(ObligationType.COLLISION)),
                    displayDate(carStates.get(ObligationType.STK)),
                    displayStatus(carStates.get(ObligationType.STK)),
                    displayDate(carStates.get(ObligationType.EK)),
                    displayStatus(carStates.get(ObligationType.EK)),
                    vignetteCountries.contains("SK"),
                    displayDate(carStates.get(ObligationType.VIGNETTE_SK)),
                    displayStatus(carStates.get(ObligationType.VIGNETTE_SK)),
                    vignetteCountries.contains("CZ"),
                    displayDate(carStates.get(ObligationType.VIGNETTE_CZ)),
                    displayStatus(carStates.get(ObligationType.VIGNETTE_CZ)),
                    vignetteCountries.contains("AT"),
                    displayDate(carStates.get(ObligationType.VIGNETTE_AT)),
                    displayStatus(carStates.get(ObligationType.VIGNETTE_AT))
            );
            carLines.add(line);

            for (Map.Entry<ObligationType, ObligationState> entry : carStates.entrySet()) {
                if (entry.getValue().getStatus() == ExpiryStatus.EXPIRING || entry.getValue().getStatus() == ExpiryStatus.EXPIRED) {
                    warnings.add(car.getLicensePlate() + ": "
                            + humanObligation(entry.getKey(), locale)
                            + " (" + entry.getValue().getStatus() + ")");
                }
            }
        }

        List<String> recentRuns = checkRunLogRepository.findRecentRuns(10).stream()
                .map(run -> run.getCheckType() + " - " + run.getStatus() + " - " + Optional.ofNullable(run.getMessage()).orElse(""))
                .toList();

        return new DailySummaryModel(generatedAt, warnings, carLines, recentRuns);
    }

    private String displayDate(ObligationState state) {
        if (state == null || state.getExpiryDate() == null) {
            return "-";
        }
        return state.getExpiryDate().toString();
    }

    private String displayStatus(ObligationState state) {
        if (state == null || state.getStatus() == null) {
            return ExpiryStatus.UNKNOWN.name();
        }
        return state.getStatus().name();
    }

    private List<String> parseRecipients(String recipientsCsv) {
        if (recipientsCsv == null || recipientsCsv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(recipientsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private NotificationLog createLog(
            Long carId,
            Long insurancePolicyId,
            ObligationType obligationType,
            NotificationType type,
            String subject,
            String recipient,
            LocalDate dedupeDate,
            Long checkRunId
    ) {
        NotificationLog log = new NotificationLog();
        log.setCarId(carId);
        log.setInsurancePolicyId(insurancePolicyId);
        log.setObligationType(obligationType);
        log.setNotificationType(type);
        log.setChannel(NotificationChannel.EMAIL);
        log.setSubject(subject);
        log.setRecipient(recipient);
        log.setStatus(NotificationStatus.PENDING);
        log.setCheckRunId(checkRunId);
        log.setDedupeDate(dedupeDate == null ? null : dedupeDate.toString());
        return log;
    }

    private void updateLogFromSendResult(NotificationLog log, SendResult result) {
        if (result.success()) {
            log.setStatus(NotificationStatus.SENT);
            log.setProviderMessageId(result.messageId());
            log.setSentAt(LocalDateTime.now(clock));
        } else {
            log.setStatus(NotificationStatus.FAILED);
            log.setProviderMessageId(result.errorMessage());
            log.setSentAt(LocalDateTime.now(clock));
        }
    }

    private String humanObligation(ObligationType type, Locale locale) {
        if (type == null) {
            return "Unknown";
        }
        boolean sk = locale.getLanguage().equals("sk");
        return switch (type) {
            case PZP -> sk ? "PZP" : "PZP";
            case COLLISION -> sk ? "Havarijne poistenie" : "Collision insurance";
            case STK -> "STK";
            case EK -> "EK";
            case VIGNETTE_SK -> sk ? "Dialnicna znamka SK" : "Vignette SK";
            case VIGNETTE_CZ -> sk ? "Dialnicna znamka CZ" : "Vignette CZ";
            case VIGNETTE_AT -> sk ? "Dialnicna znamka AT" : "Vignette AT";
        };
    }
}
