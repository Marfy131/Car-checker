package com.carwatch.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.carwatch.application.notification.ReminderNotificationService;
import com.carwatch.application.schedule.ScheduledWorkflowProvider;
import com.carwatch.domain.schedule.CheckCommand;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import com.carwatch.domain.setting.AppSetting;
import com.carwatch.domain.setting.AppSettingRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ScheduledWorkflowProviderIT {

    @Container
    static final GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"));

    static {
        mailpit.withExposedPorts(1025, 8025);
    }

    @DynamicPropertySource
    static void configureMailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    @Autowired
    private ReminderNotificationService reminderNotificationService;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Test
    void executeDailySummarySendsEmailViaMailpit() throws Exception {
        Optional<AppSetting> existing = appSettingRepository.findByKey("app.email.recipients");
        if (existing.isEmpty()) {
            AppSetting setting = new AppSetting();
            setting.setSettingKey("app.email.recipients");
            setting.setSettingValue("admin@example.com");
            appSettingRepository.save(setting);
        } else {
            existing.get().setSettingValue("admin@example.com");
            appSettingRepository.save(existing.get());
        }

        ScheduledWorkflowProvider provider = new ScheduledWorkflowProvider(
                CheckType.DAILY_SUMMARY_EMAIL,
                reminderNotificationService,
                appSettingRepository);

        CheckOutcome outcome = provider.execute(new CheckCommand(null, null, null, CheckType.DAILY_SUMMARY_EMAIL));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(outcome.message()).contains("Daily summaries sent");

        String mailpitApiUrl = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mailpitApiUrl))
                .GET()
                .build();

        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isBetween(200, 299);
            assertThat(response.body()).contains("admin@example.com");
        }
    }
}
