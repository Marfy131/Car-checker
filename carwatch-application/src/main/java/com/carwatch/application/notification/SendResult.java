package com.carwatch.application.notification;

public record SendResult(
        boolean success,
        String messageId,
        String errorMessage
) {
    public static SendResult ok(String messageId) {
        return new SendResult(true, messageId, null);
    }

    public static SendResult failed(String errorMessage) {
        return new SendResult(false, null, errorMessage);
    }
}
