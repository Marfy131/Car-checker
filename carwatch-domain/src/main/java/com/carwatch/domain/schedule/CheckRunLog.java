package com.carwatch.domain.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("check_run_log")
public class CheckRunLog {

    @Id
    private Long id;

    @Column("schedule_id")
    private Long scheduleId;

    @Column("car_id")
    private Long carId;

    @Column("insurance_policy_id")
    private Long insurancePolicyId;

    @Column("check_type")
    private CheckType checkType;

    @Column("started_at")
    private LocalDateTime startedAt;

    @Column("finished_at")
    private LocalDateTime finishedAt;

    private RunStatus status;

    private String message;

    @Column("expiry_date_found")
    private LocalDate expiryDateFound;

    @Column("findings_json")
    private String findingsJson;

    @Column("duration_ms")
    private Long durationMs;

    @Column("trace_id")
    private String traceId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
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

    public CheckType getCheckType() {
        return checkType;
    }

    public void setCheckType(CheckType checkType) {
        this.checkType = checkType;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDate getExpiryDateFound() {
        return expiryDateFound;
    }

    public void setExpiryDateFound(LocalDate expiryDateFound) {
        this.expiryDateFound = expiryDateFound;
    }

    public String getFindingsJson() {
        return findingsJson;
    }

    public void setFindingsJson(String findingsJson) {
        this.findingsJson = findingsJson;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
