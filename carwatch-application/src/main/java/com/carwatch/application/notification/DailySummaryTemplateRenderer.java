package com.carwatch.application.notification;

import java.util.Locale;

public interface DailySummaryTemplateRenderer {

    String render(DailySummaryModel model, Locale locale);
}
