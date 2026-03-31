package com.carwatch.domain.insurance;

import com.carwatch.domain.obligation.ExpiryStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("insurance_policy")
public class InsurancePolicy {

    @Id
    private Long id;

    @Column("car_id")
    private Long carId;

    @Column("policy_type")
    private PolicyType policyType;

    private boolean enabled;

    @Column("insurer_name")
    private String insurerName;

    @Column("policy_number")
    private String policyNumber;

    @Column("expiry_date")
    private LocalDate expiryDate;

    private ExpiryStatus status;

    @Column("check_mode")
    private CheckMode checkMode;

    @Column("warning_days_before")
    private int warningDaysBefore;

    @Column("last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column("last_successful_check_at")
    private LocalDateTime lastSuccessfulCheckAt;

    private String notes;

    private int version;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

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

    public PolicyType getPolicyType() {
        return policyType;
    }

    public void setPolicyType(PolicyType policyType) {
        this.policyType = policyType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getInsurerName() {
        return insurerName;
    }

    public void setInsurerName(String insurerName) {
        this.insurerName = insurerName;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public void setPolicyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public ExpiryStatus getStatus() {
        return status;
    }

    public void setStatus(ExpiryStatus status) {
        this.status = status;
    }

    public CheckMode getCheckMode() {
        return checkMode;
    }

    public void setCheckMode(CheckMode checkMode) {
        this.checkMode = checkMode;
    }

    public int getWarningDaysBefore() {
        return warningDaysBefore;
    }

    public void setWarningDaysBefore(int warningDaysBefore) {
        this.warningDaysBefore = warningDaysBefore;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public LocalDateTime getLastSuccessfulCheckAt() {
        return lastSuccessfulCheckAt;
    }

    public void setLastSuccessfulCheckAt(LocalDateTime lastSuccessfulCheckAt) {
        this.lastSuccessfulCheckAt = lastSuccessfulCheckAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
