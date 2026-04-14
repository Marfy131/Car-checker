package com.carwatch.domain.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.carwatch.domain.obligation.ObligationType;
import com.carwatch.domain.vignette.CountryCode;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class CheckTypeMappingTest {

    @Test
    void findsObligationTypeForMappedCheckTypes() {
        assertEquals(ObligationType.PZP, CheckTypeMapping.findObligationType(CheckType.PZP_CHECK).orElseThrow());
        assertEquals(ObligationType.COLLISION, CheckTypeMapping.findObligationType(CheckType.COLLISION_INSURANCE_CHECK).orElseThrow());
        assertEquals(ObligationType.STK, CheckTypeMapping.findObligationType(CheckType.STK_CHECK).orElseThrow());
        assertEquals(ObligationType.EK, CheckTypeMapping.findObligationType(CheckType.EK_CHECK).orElseThrow());
        assertEquals(ObligationType.VIGNETTE_SK, CheckTypeMapping.findObligationType(CheckType.VIGNETTE_SK_CHECK).orElseThrow());
        assertEquals(ObligationType.VIGNETTE_CZ, CheckTypeMapping.findObligationType(CheckType.VIGNETTE_CZ_CHECK).orElseThrow());
        assertEquals(ObligationType.VIGNETTE_AT, CheckTypeMapping.findObligationType(CheckType.VIGNETTE_AT_CHECK).orElseThrow());
    }

    @Test
    void returnsEmptyForCheckTypesWithoutObligationMapping() {
        assertTrue(CheckTypeMapping.findObligationType(CheckType.DAILY_SUMMARY_EMAIL).isEmpty());
        assertTrue(CheckTypeMapping.findObligationType(CheckType.DAILY_REMINDER_SCAN).isEmpty());
    }

    @Test
    void findsVignetteCountryForMappedCheckTypes() {
        assertEquals(CountryCode.SK, CheckTypeMapping.findVignetteCountry(CheckType.VIGNETTE_SK_CHECK).orElseThrow());
        assertEquals(CountryCode.CZ, CheckTypeMapping.findVignetteCountry(CheckType.VIGNETTE_CZ_CHECK).orElseThrow());
        assertEquals(CountryCode.AT, CheckTypeMapping.findVignetteCountry(CheckType.VIGNETTE_AT_CHECK).orElseThrow());
    }

    @Test
    void returnsEmptyForCheckTypesWithoutVignetteCountryMapping() {
        assertTrue(CheckTypeMapping.findVignetteCountry(CheckType.STK_CHECK).isEmpty());
    }

    @Test
    void findsVignetteCheckTypeForSupportedCountries() {
        assertEquals(CheckType.VIGNETTE_SK_CHECK, CheckTypeMapping.findVignetteCheckType(CountryCode.SK).orElseThrow());
        assertEquals(CheckType.VIGNETTE_CZ_CHECK, CheckTypeMapping.findVignetteCheckType(CountryCode.CZ).orElseThrow());
        assertEquals(CheckType.VIGNETTE_AT_CHECK, CheckTypeMapping.findVignetteCheckType(CountryCode.AT).orElseThrow());
    }

    @Test
    void exposesAllSupportedVignetteCountries() {
        assertEquals(
            EnumSet.of(CountryCode.SK, CountryCode.CZ, CountryCode.AT),
            CheckTypeMapping.supportedVignetteCountries()
        );
    }

    @Test
    void roundTripsSupportedVignetteMappings() {
        for (CountryCode countryCode : CheckTypeMapping.supportedVignetteCountries()) {
            CheckType checkType = CheckTypeMapping.findVignetteCheckType(countryCode).orElseThrow();
            assertEquals(countryCode, CheckTypeMapping.findVignetteCountry(checkType).orElseThrow());
        }
    }

    @Test
    void returnsEmptyForUnsupportedVignetteCountry() {
        assertTrue(CheckTypeMapping.findVignetteCheckType(CountryCode.HU).isEmpty());
    }
}
