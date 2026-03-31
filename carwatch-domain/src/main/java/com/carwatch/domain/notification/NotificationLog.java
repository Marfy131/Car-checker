package com.carwatch.domain.notification;

import java.time.LocalDateTime;
import com.carwatch.domain.obligation.ObligationType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("notification_log")
public class NotificationLog {

    @Id
    private Long id;

    @Column("car_id")
    private Long carId;

    @Column("insurance_policy_id")
    private Long insurancePolicyId;

    @Column("obligation_type")
    private ObligationType obligationType;

    @Column("notification_type")
    private NotificationType notificationType;

    private NotificationChannel channel;

    private String subject;

    private String recipient;

    @Column("sent_at")
    private LocalDateTime sentAt;

    private NotificationStatus status;

    @Column("provider_message_id")
    private String providerMessageId;

    @Column("check_run_id")
    private Long checkRunId;

    @Column("dedupe_date")
    private String dedupeDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCarId() {
        return carId;
    }

    public void setCarId(Long carId) {
        this.carId = carId;
    }

    public Long getInsurancePolicyId() {
        return insurancePolicyId;
    }

    public void setInsurancePolicyId(Long insurancePolicyId) {
        this.insurancePolicyId = insurancePolicyId;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public Long getCheckRunId() {
        return checkRunId;
    }

    public void setCheckRunId(Long checkRunId) {
        this.checkRunId = checkRunId;
    }

    public ObligationType getObligationType() {
        return obligationType;
    }

    public void setObligationType(ObligationType obligationType) {
        this.obligationType = obligationType;
    }

    public String getDedupeDate() {
        return dedupeDate;
    }

    public void setDedupeDate(String dedupeDate) {
        this.dedupeDate = dedupeDate;
    }
}
