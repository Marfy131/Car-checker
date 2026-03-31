package com.carwatch.infrastructure.email;

import com.carwatch.application.notification.EmailMessage;
import com.carwatch.application.notification.EmailSender;
import com.carwatch.application.notification.SendResult;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SpringMailEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SpringMailEmailSender.class);

    private final JavaMailSender mailSender;

    public SpringMailEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public SendResult send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);
            mailSender.send(mime);
            String messageId = mime.getMessageID();
            log.info("Email sent successfully to={}, subject={}, messageId={}",
                    message.to(), message.subject(), messageId);
            return SendResult.ok(messageId);
        } catch (Exception e) {
            log.error("Failed to send email to={}, subject={}",
                    message.to(), message.subject(), e);
            return SendResult.failed(e.getMessage());
        }
    }
}
