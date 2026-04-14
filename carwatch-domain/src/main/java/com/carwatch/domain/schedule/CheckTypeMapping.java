package com.carwatch.domain.schedule;

import com.carwatch.domain.obligation.ObligationType;
import com.carwatch.domain.vignette.CountryCode;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CheckTypeMapping {

    private static final Map<CheckType, MappingEntry> MAPPINGS = new EnumMap<>(CheckType.class);
    private static final Map<CountryCode, CheckType> VIGNETTE_CHECK_TYPES = new EnumMap<>(CountryCode.class);

    static {
        register(CheckType.PZP_CHECK, ObligationType.PZP, null);
        register(CheckType.COLLISION_INSURANCE_CHECK, ObligationType.COLLISION, null);
        register(CheckType.STK_CHECK, ObligationType.STK, null);
        register(CheckType.EK_CHECK, ObligationType.EK, null);
        register(CheckType.VIGNETTE_SK_CHECK, ObligationType.VIGNETTE_SK, CountryCode.SK);
        register(CheckType.VIGNETTE_CZ_CHECK, ObligationType.VIGNETTE_CZ, CountryCode.CZ);
        register(CheckType.VIGNETTE_AT_CHECK, ObligationType.VIGNETTE_AT, CountryCode.AT);
    }

    private CheckTypeMapping() {
    }

    public static Optional<ObligationType> findObligationType(CheckType checkType) {
        return Optional.ofNullable(MAPPINGS.get(checkType))
            .map(MappingEntry::obligationType);
    }

    public static Optional<CountryCode> findVignetteCountry(CheckType checkType) {
        return Optional.ofNullable(MAPPINGS.get(checkType))
            .map(MappingEntry::countryCode);
    }

    public static Optional<CheckType> findVignetteCheckType(CountryCode countryCode) {
        return Optional.ofNullable(VIGNETTE_CHECK_TYPES.get(countryCode));
    }

    public static Set<CountryCode> supportedVignetteCountries() {
        return EnumSet.copyOf(VIGNETTE_CHECK_TYPES.keySet());
    }

    private static void register(CheckType checkType, ObligationType obligationType, CountryCode countryCode) {
        MAPPINGS.put(checkType, new MappingEntry(obligationType, countryCode));
        if (countryCode != null) {
            VIGNETTE_CHECK_TYPES.put(countryCode, checkType);
        }
    }

    private record MappingEntry(ObligationType obligationType, CountryCode countryCode) {
    }
}
