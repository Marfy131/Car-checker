package com.carwatch.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.carwatch.application.notification.DailySummaryModel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class ThymeleafDailySummaryTemplateRendererTest {

    private ThymeleafDailySummaryTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        templateResolver.setCacheable(false);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);

        renderer = new ThymeleafDailySummaryTemplateRenderer(templateEngine);
    }

    @Test
    void rendersEnglishTemplateWithStructuredCarItems() {
        String html = renderer.render(summaryModel(), Locale.ENGLISH);

        assertThat(html).contains("Vehicles:");
        assertThat(html).contains("Skoda Octavia (BA-123AB)");
        assertThat(html).contains("STK");
        assertThat(html).contains("2026-05-01");
        assertThat(html).contains("VALID");
    }

    @Test
    void rendersSlovakTemplateWithStructuredCarItems() {
        String html = renderer.render(summaryModel(), Locale.forLanguageTag("sk"));

        assertThat(html).contains("Vozidlá:");
        assertThat(html).contains("Skoda Octavia (BA-123AB)");
        assertThat(html).contains("PZP");
        assertThat(html).contains("2026-06-15");
        assertThat(html).contains("EXPIRING");
    }

    private DailySummaryModel summaryModel() {
        return new DailySummaryModel(
                "2026-04-13 08:00",
                List.of("STK expires soon"),
                List.of(new DailySummaryModel.CarSummary(
                        "Skoda Octavia",
                        "BA-123AB",
                        List.of(
                                new DailySummaryModel.SummaryItem("PZP", "2026-06-15", "EXPIRING"),
                                new DailySummaryModel.SummaryItem("STK", "2026-05-01", "VALID")
                        ))),
                List.of("STK check for Skoda Octavia finished successfully"));
    }
}
