package com.carwatch.infrastructure.email;

import com.carwatch.application.notification.EmailMessage;
import com.carwatch.application.notification.SendResult;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringMailEmailSenderTest {

    @Test
    void sendReturnsSuccessWhenMailSenderSendsMessage() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        SpringMailEmailSender sender = new SpringMailEmailSender(mailSender);
        EmailMessage message = new EmailMessage("john@example.com", "Subject", "<b>Hello</b>");

        SendResult result = sender.send(message);

        verify(mailSender).send(mimeMessage);
        assertThat(result.success()).isTrue();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void sendReturnsFailureWhenMailSenderThrowsException() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(MimeMessage.class));

        SpringMailEmailSender sender = new SpringMailEmailSender(mailSender);
        EmailMessage message = new EmailMessage("john@example.com", "Subject", "<b>Hello</b>");

        SendResult result = sender.send(message);

        assertThat(result.success()).isFalse();
        assertThat(result.messageId()).isNull();
        assertThat(result.errorMessage()).isEqualTo("SMTP unavailable");
    }
}
