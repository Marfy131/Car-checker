package com.carwatch.application.notification;

public record EmailMessage(
        String to,
        String subject,
        String htmlBody
) {
}
