package com.carwatch.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class CarForm {

    private Long id;

    @NotBlank(message = "{validation.required}")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = "^[A-Za-z0-9\\-\\s]{3,15}$", message = "{validation.licensePlate.format}")
    @Size(max = 50)
    private String licensePlate;

    @NotNull(message = "{validation.required}")
    private LocalDate registrationDate;

    @NotBlank(message = "{validation.required}")
    @Size(max = 100)
    private String vin;

    private boolean vignetteSk;
    private boolean vignetteCz;
    private boolean vignetteAt;
    private int version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public boolean isVignetteSk() {
        return vignetteSk;
    }

    public void setVignetteSk(boolean vignetteSk) {
        this.vignetteSk = vignetteSk;
    }

    public boolean isVignetteCz() {
        return vignetteCz;
    }

    public void setVignetteCz(boolean vignetteCz) {
        this.vignetteCz = vignetteCz;
    }

    public boolean isVignetteAt() {
        return vignetteAt;
    }

    public void setVignetteAt(boolean vignetteAt) {
        this.vignetteAt = vignetteAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
