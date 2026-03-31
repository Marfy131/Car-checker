package com.carwatch.application.notification;

public interface EmailSender {
    SendResult send(EmailMessage message);
}
