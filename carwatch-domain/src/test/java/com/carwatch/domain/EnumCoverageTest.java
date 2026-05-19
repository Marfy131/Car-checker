package com.carwatch.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.carwatch.domain.insurance.CheckMode;
import com.carwatch.domain.insurance.PolicyType;
import com.carwatch.domain.notification.NotificationChannel;
import com.carwatch.domain.notification.NotificationStatus;
import com.carwatch.domain.notification.NotificationType;
import com.carwatch.domain.obligation.ExpiryStatus;
import com.carwatch.domain.obligation.ObligationType;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import com.carwatch.domain.vignette.CountryCode;
import org.junit.jupiter.api.Test;

class EnumCoverageTest {

    @Test
    void allEnumsExposeExpectedConstants() {
        assertThat(CheckMode.values()).containsExactly(CheckMode.ONLINE, CheckMode.MANUAL);
        assertThat(PolicyType.values()).containsExactly(PolicyType.PZP, PolicyType.COLLISION);
        assertThat(NotificationChannel.values()).containsExactly(NotificationChannel.EMAIL);
        assertThat(NotificationStatus.values()).containsExactly(NotificationStatus.PENDING, NotificationStatus.SENT, NotificationStatus.FAILED);
        assertThat(NotificationType.values()).containsExactly(
                NotificationType.EXPIRY_WARNING,
                NotificationType.EXPIRED,
                NotificationType.DAILY_SUMMARY,
                NotificationType.CHECK_FAILURE
        );
        assertThat(ExpiryStatus.values()).containsExactly(
                ExpiryStatus.VALID, ExpiryStatus.EXPIRING, ExpiryStatus.EXPIRED, ExpiryStatus.UNKNOWN, ExpiryStatus.ERROR
        );
        assertThat(ObligationType.values()).containsExactly(
                ObligationType.PZP,
                ObligationType.COLLISION,
                ObligationType.STK,
                ObligationType.EK,
                ObligationType.VIGNETTE_SK,
                ObligationType.VIGNETTE_CZ,
                ObligationType.VIGNETTE_AT,
                ObligationType.VIGNETTE_HU
        );
        assertThat(CheckType.values()).contains(
                CheckType.PZP_CHECK,
                CheckType.COLLISION_INSURANCE_CHECK,
                CheckType.STK_CHECK,
                CheckType.EK_CHECK,
                CheckType.VIGNETTE_SK_CHECK,
                CheckType.VIGNETTE_CZ_CHECK,
                CheckType.VIGNETTE_AT_CHECK,
                CheckType.VIGNETTE_HU_CHECK,
                CheckType.DAILY_SUMMARY_EMAIL,
                CheckType.DAILY_REMINDER_SCAN
        );
        assertThat(RunStatus.values()).containsExactly(RunStatus.SUCCESS, RunStatus.WARNING, RunStatus.ERROR, RunStatus.NO_DATA);
        assertThat(CountryCode.values()).containsExactly(CountryCode.SK, CountryCode.CZ, CountryCode.AT, CountryCode.HU);
    }
}
