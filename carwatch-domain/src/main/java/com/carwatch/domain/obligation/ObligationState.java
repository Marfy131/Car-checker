package com.carwatch.domain.obligation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("obligation_state")
public class ObligationState {

    @Id
    private Long id;

    @Column("car_id")
    private Long carId;

    @Column("obligation_type")
    private ObligationType obligationType;

    @Column("expiry_date")
    private LocalDate expiryDate;

    private ExpiryStatus status;

    @Column("last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column("last_successful_check_at")
    private LocalDateTime lastSuccessfulCheckAt;

    @Column("source_system")
    private String sourceSystem;

    @Column("details_json")
    private String detailsJson;

    @Column("manual_override")
    private boolean manualOverride;

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

    public ObligationType getObligationType() {
        return obligationType;
    }

    public void setObligationType(ObligationType obligationType) {
        this.obligationType = obligationType;
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

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public boolean isManualOverride() {
        return manualOverride;
    }

    public void setManualOverride(boolean manualOverride) {
        this.manualOverride = manualOverride;
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
