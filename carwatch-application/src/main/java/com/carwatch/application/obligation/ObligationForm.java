package com.carwatch.application.obligation;

import java.time.LocalDate;

public class ObligationForm {

    private LocalDate pzpExpiry;
    private LocalDate collisionExpiry;
    private LocalDate stkExpiry;
    private LocalDate ekExpiry;
    private LocalDate vignetteSkExpiry;
    private LocalDate vignetteCzExpiry;
    private LocalDate vignetteAtExpiry;
    private LocalDate vignetteHuExpiry;

    public LocalDate getPzpExpiry() { return pzpExpiry; }
    public void setPzpExpiry(LocalDate pzpExpiry) { this.pzpExpiry = pzpExpiry; }

    public LocalDate getCollisionExpiry() { return collisionExpiry; }
    public void setCollisionExpiry(LocalDate collisionExpiry) { this.collisionExpiry = collisionExpiry; }

    public LocalDate getStkExpiry() { return stkExpiry; }
    public void setStkExpiry(LocalDate stkExpiry) { this.stkExpiry = stkExpiry; }

    public LocalDate getEkExpiry() { return ekExpiry; }
    public void setEkExpiry(LocalDate ekExpiry) { this.ekExpiry = ekExpiry; }

    public LocalDate getVignetteSkExpiry() { return vignetteSkExpiry; }
    public void setVignetteSkExpiry(LocalDate vignetteSkExpiry) { this.vignetteSkExpiry = vignetteSkExpiry; }

    public LocalDate getVignetteCzExpiry() { return vignetteCzExpiry; }
    public void setVignetteCzExpiry(LocalDate vignetteCzExpiry) { this.vignetteCzExpiry = vignetteCzExpiry; }

    public LocalDate getVignetteAtExpiry() { return vignetteAtExpiry; }
    public void setVignetteAtExpiry(LocalDate vignetteAtExpiry) { this.vignetteAtExpiry = vignetteAtExpiry; }

    public LocalDate getVignetteHuExpiry() { return vignetteHuExpiry; }
    public void setVignetteHuExpiry(LocalDate vignetteHuExpiry) { this.vignetteHuExpiry = vignetteHuExpiry; }
}
