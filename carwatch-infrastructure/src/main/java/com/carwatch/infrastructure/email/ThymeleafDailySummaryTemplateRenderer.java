package com.carwatch.infrastructure.email;

import com.carwatch.application.notification.DailySummaryModel;
import com.carwatch.application.notification.DailySummaryTemplateRenderer;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class ThymeleafDailySummaryTemplateRenderer implements DailySummaryTemplateRenderer {

    private final TemplateEngine templateEngine;

    public ThymeleafDailySummaryTemplateRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    public String render(DailySummaryModel model, Locale locale) {
        Context context = new Context(locale);
        context.setVariable("generatedAt", model.generatedAt());
        context.setVariable("warnings", model.warnings());
        context.setVariable("cars", model.cars());
        context.setVariable("recentRuns", model.recentRuns());
        String templateName = locale.getLanguage().equals("sk")
                ? "email/daily-summary_sk"
                : "email/daily-summary_en";
        return templateEngine.process(templateName, context);
    }
}
