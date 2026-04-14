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
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderNotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ObligationStateRepository obligationStateRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final CarRepository carRepository;
    private final EmailSender emailSender;
    private final DailySummaryTemplateRenderer dailySummaryTemplateRenderer;
    private final CheckRunLogRepository checkRunLogRepository;
    private final CarVignetteSelectionRepository carVignetteSelectionRepository;
    private final Clock clock;
    private final int dailySummaryRecentRunLimit;

    @Autowired
    public ReminderNotificationService(
            ObligationStateRepository obligationStateRepository,
            NotificationLogRepository notificationLogRepository,
            CarRepository carRepository,
            EmailSender emailSender,
            DailySummaryTemplateRenderer dailySummaryTemplateRenderer,
            CheckRunLogRepository checkRunLogRepository,
            CarVignetteSelectionRepository carVignetteSelectionRepository,
            Clock clock,
            @Value("${carwatch.notifications.daily-summary.recent-runs:10}") int dailySummaryRecentRunLimit) {
        this.obligationStateRepository = obligationStateRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.carRepository = carRepository;
        this.emailSender = emailSender;
        this.dailySummaryTemplateRenderer = dailySummaryTemplateRenderer;
        this.checkRunLogRepository = checkRunLogRepository;
        this.carVignetteSelectionRepository = carVignetteSelectionRepository;
        this.clock = clock;
        this.dailySummaryRecentRunLimit = dailySummaryRecentRunLimit;
    }

    ReminderNotificationService(
            ObligationStateRepository obligationStateRepository,
            NotificationLogRepository notificationLogRepository,
            CarRepository carRepository,
            EmailSender emailSender,
            DailySummaryTemplateRenderer dailySummaryTemplateRenderer,
            CheckRunLogRepository checkRunLogRepository,
            CarVignetteSelectionRepository carVignetteSelectionRepository,
            Clock clock) {
        this(
                obligationStateRepository,
                notificationLogRepository,
                carRepository,
                emailSender,
                dailySummaryTemplateRenderer,
                checkRunLogRepository,
                carVignetteSelectionRepository,
                clock,
                10);
    }



    int sendImmediateReminderForState(ObligationState state, String recipient) {
        if (!isReminderCandidate(state)) {
            return 0;
        }
        NotificationType type = resolveReminderType(state);
        LocalDate today = LocalDate.now(clock);
        if (notificationLogRepository.existsByCarIdAndObligationTypeAndTypeAndDate(
                state.getCarId(),
                state.getObligationType(),
                type,
                today)) {
            return 0;
        }
        return sendReminderForState(state, recipient, today, type, loadCar(state.getCarId()));
    }

    @Transactional
    public int sendDailyReminders(String recipient) {
        int sent = 0;
        LocalDate today = LocalDate.now(clock);
        Map<Long, Car> carsById = loadCarsById();
        Set<ReminderDedupeKey> sentToday = loadReminderDedupeKeys(today);
        List<ObligationState> states = obligationStateRepository.findAll();
        states.sort(Comparator.comparing(ObligationState::getCarId, Comparator.nullsLast(Long::compareTo)));
        for (ObligationState state : states) {
            if (isReminderCandidate(state)) {
                NotificationType type = resolveReminderType(state);
                ReminderDedupeKey dedupeKey = reminderDedupeKey(state, type);
                if (!sentToday.contains(dedupeKey)) {
                    sent += sendReminderForState(state, recipient, today, type, carsById.get(state.getCarId()));
                    sentToday.add(dedupeKey);
                }
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
                    NotificationType.DAILY_SUMMARY,
                    subject,
                    recipient,
                    today);
            SendResult sendResult = emailSender.send(new EmailMessage(recipient, subject, htmlBody));
            updateLogFromSendResult(log, sendResult);
            notificationLogRepository.save(log);
            if (sendResult.success()) {
                sent++;
            }
        }
        return sent;
    }

    private DailySummaryModel buildSummaryModel(String generatedAt, Locale locale) {
        Map<Long, Map<ObligationType, ObligationState>> statesByCar = aggregateCarStates(obligationStateRepository.findAll());
        Map<Long, Set<String>> enabledVignettes = aggregateEnabledVignettes(carVignetteSelectionRepository.findAll());

        List<DailySummaryModel.CarSummary> carSummaries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        buildCarSummariesAndWarnings(carRepository.findAll(), statesByCar, enabledVignettes, locale, carSummaries, warnings);

        List<String> recentRuns = checkRunLogRepository.findRecentRuns(dailySummaryRecentRunLimit).stream()
                .map(run -> run.getCheckType() + " - " + run.getStatus() + " - "
                        + Optional.ofNullable(run.getMessage()).orElse(""))
                .toList();

        return new DailySummaryModel(generatedAt, warnings, carSummaries, recentRuns);
    }

    private Map<Long, Map<ObligationType, ObligationState>> aggregateCarStates(List<ObligationState> states) {
        Map<Long, Map<ObligationType, ObligationState>> statesByCar = new HashMap<>();
        for (ObligationState state : states) {
            if (state.getCarId() == null) {
                continue;
            }
            statesByCar.computeIfAbsent(state.getCarId(), key -> new java.util.EnumMap<>(ObligationType.class))
                    .put(state.getObligationType(), state);
        }
        return statesByCar;
    }

    private Map<Long, Set<String>> aggregateEnabledVignettes(List<CarVignetteSelection> selections) {
        Map<Long, Set<String>> enabledVignettes = new HashMap<>();
        for (CarVignetteSelection selection : selections) {
            if (selection.isEnabled()) {
                enabledVignettes.computeIfAbsent(selection.getCarId(), key -> new HashSet<>())
                        .add(selection.getCountry().name());
            }
        }
        return enabledVignettes;
    }

    private void buildCarSummariesAndWarnings(
            List<Car> cars,
            Map<Long, Map<ObligationType, ObligationState>> statesByCar,
            Map<Long, Set<String>> enabledVignettes,
            Locale locale,
            List<DailySummaryModel.CarSummary> carSummariesOut,
            List<String> warningsOut) {
        for (Car car : cars) {
            Map<ObligationType, ObligationState> carStates = statesByCar.getOrDefault(car.getId(), Map.of());
            Set<String> vignetteCountries = enabledVignettes.getOrDefault(car.getId(), Set.of());

            List<DailySummaryModel.SummaryItem> items = new ArrayList<>();
            items.add(summaryItem(ObligationType.PZP, carStates.get(ObligationType.PZP), locale));
            items.add(summaryItem(ObligationType.COLLISION, carStates.get(ObligationType.COLLISION), locale));
            items.add(summaryItem(ObligationType.STK, carStates.get(ObligationType.STK), locale));
            items.add(summaryItem(ObligationType.EK, carStates.get(ObligationType.EK), locale));
            addEnabledVignetteItem(items, vignetteCountries, "SK", ObligationType.VIGNETTE_SK, carStates, locale);
            addEnabledVignetteItem(items, vignetteCountries, "CZ", ObligationType.VIGNETTE_CZ, carStates, locale);
            addEnabledVignetteItem(items, vignetteCountries, "AT", ObligationType.VIGNETTE_AT, carStates, locale);

            carSummariesOut.add(new DailySummaryModel.CarSummary(
                    car.getName(),
                    car.getLicensePlate(),
                    items));

            for (Map.Entry<ObligationType, ObligationState> entry : carStates.entrySet()) {
                if (entry.getValue().getStatus() == ExpiryStatus.EXPIRING
                        || entry.getValue().getStatus() == ExpiryStatus.EXPIRED) {
                    warningsOut.add(car.getLicensePlate() + ": "
                            + humanObligation(entry.getKey(), locale)
                            + " (" + entry.getValue().getStatus() + ")");
                }
            }
        }
    }

    private void addEnabledVignetteItem(
            List<DailySummaryModel.SummaryItem> items,
            Set<String> enabledVignetteCountries,
            String countryCode,
            ObligationType obligationType,
            Map<ObligationType, ObligationState> carStates,
            Locale locale) {
        if (!enabledVignetteCountries.contains(countryCode)) {
            return;
        }
        items.add(summaryItem(obligationType, carStates.get(obligationType), locale));
    }

    private DailySummaryModel.SummaryItem summaryItem(
            ObligationType obligationType,
            ObligationState state,
            Locale locale) {
        return new DailySummaryModel.SummaryItem(
                summaryItemLabel(obligationType, locale),
                displayDate(state),
                displayStatus(state));
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

    private boolean isReminderCandidate(ObligationState state) {
        return state.getCarId() != null
                && state.getObligationType() != null
                && (state.getStatus() == ExpiryStatus.EXPIRING || state.getStatus() == ExpiryStatus.EXPIRED);
    }

    private NotificationType resolveReminderType(ObligationState state) {
        return state.getStatus() == ExpiryStatus.EXPIRED
                ? NotificationType.EXPIRED
                : NotificationType.EXPIRY_WARNING;
    }

    private Set<ReminderDedupeKey> loadReminderDedupeKeys(LocalDate today) {
        Set<ReminderDedupeKey> dedupeKeys = new HashSet<>();
        for (NotificationLog log : notificationLogRepository.findReminderLogsByDate(today)) {
            ReminderDedupeKey dedupeKey = reminderDedupeKey(log);
            if (dedupeKey != null) {
                dedupeKeys.add(dedupeKey);
            }
        }
        return dedupeKeys;
    }

    private Map<Long, Car> loadCarsById() {
        return carRepository.findAll().stream()
                .filter(car -> car.getId() != null)
                .collect(java.util.stream.Collectors.toMap(Car::getId, Function.identity(), (left, right) -> left));
    }

    private Car loadCar(Long carId) {
        if (carId == null) {
            return null;
        }
        return carRepository.findById(carId).orElse(null);
    }

    private int sendReminderForState(
            ObligationState state,
            String recipient,
            LocalDate today,
            NotificationType type,
            Car car) {
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
                state.getObligationType(),
                type,
                subject,
                recipient,
                today);
        SendResult sendResult = emailSender.send(new EmailMessage(recipient, subject, body));
        updateLogFromSendResult(log, sendResult);
        notificationLogRepository.save(log);
        return sendResult.success() ? 1 : 0;
    }

    private ReminderDedupeKey reminderDedupeKey(ObligationState state, NotificationType type) {
        return new ReminderDedupeKey(state.getCarId(), state.getObligationType(), type);
    }

    private ReminderDedupeKey reminderDedupeKey(NotificationLog log) {
        if (log.getCarId() == null
                || log.getObligationType() == null
                || (log.getNotificationType() != NotificationType.EXPIRED
                && log.getNotificationType() != NotificationType.EXPIRY_WARNING)) {
            return null;
        }
        return new ReminderDedupeKey(log.getCarId(), log.getObligationType(), log.getNotificationType());
    }

    private NotificationLog createLog(
            Long carId,
            ObligationType obligationType,
            NotificationType type,
            String subject,
            String recipient,
            LocalDate dedupeDate) {
        NotificationLog log = new NotificationLog();
        log.setCarId(carId);
        log.setObligationType(obligationType);
        log.setNotificationType(type);
        log.setChannel(NotificationChannel.EMAIL);
        log.setSubject(subject);
        log.setRecipient(recipient);
        log.setStatus(NotificationStatus.PENDING);
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

    private String summaryItemLabel(ObligationType type, Locale locale) {
        if (type == null) {
            return "Unknown";
        }
        boolean sk = locale.getLanguage().equals("sk");
        return switch (type) {
            case PZP -> "PZP";
            case COLLISION -> sk ? "Havarijné poistenie" : "Collision insurance";
            case STK -> "STK";
            case EK -> "EK";
            case VIGNETTE_SK -> sk ? "Diaľničná známka SK" : "Vignette SK";
            case VIGNETTE_CZ -> sk ? "Diaľničná známka CZ" : "Vignette CZ";
            case VIGNETTE_AT -> sk ? "Diaľničná známka AT" : "Vignette AT";
        };
    }

    private String humanObligation(ObligationType type, Locale locale) {
        if (type == null) {
            return "Unknown";
        }
        boolean sk = locale.getLanguage().equals("sk");
        return switch (type) {
            case PZP -> "PZP";
            case COLLISION -> sk ? "Havarijne poistenie" : "Collision insurance";
            case STK -> "STK";
            case EK -> "EK";
            case VIGNETTE_SK -> sk ? "Dialnicna znamka SK" : "Vignette SK";
            case VIGNETTE_CZ -> sk ? "Dialnicna znamka CZ" : "Vignette CZ";
            case VIGNETTE_AT -> sk ? "Dialnicna znamka AT" : "Vignette AT";
        };
    }

    private record ReminderDedupeKey(Long carId, ObligationType obligationType, NotificationType notificationType) {
    }
}
