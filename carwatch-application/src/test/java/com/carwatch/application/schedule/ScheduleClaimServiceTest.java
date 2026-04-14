package com.carwatch.application.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckScheduleRepository;
import com.carwatch.domain.schedule.CheckType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleClaimServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-10T08:15:30Z"), ZoneOffset.UTC);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 1, 10, 8, 15, 30);

    @Mock
    private CheckScheduleRepository checkScheduleRepository;

    @Test
    void claimUsesConfiguredLockTtlForLockTimestamps() {
        CheckSchedule staleView = schedule(1L);
        CheckSchedule current = schedule(1L);
        Duration configuredLockTtl = Duration.ofMinutes(7);

        when(checkScheduleRepository.findById(1L)).thenReturn(Optional.of(current));
        when(checkScheduleRepository.save(org.mockito.ArgumentMatchers.any(CheckSchedule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleClaimService service = new ScheduleClaimService(checkScheduleRepository, FIXED_CLOCK, configuredLockTtl);

        boolean claimed = service.claim(staleView);

        assertThat(claimed).isTrue();

        ArgumentCaptor<CheckSchedule> captor = ArgumentCaptor.forClass(CheckSchedule.class);
        verify(checkScheduleRepository).save(captor.capture());
        CheckSchedule saved = captor.getValue();
        assertThat(saved.getLockUntil()).isEqualTo(FIXED_NOW.plus(configuredLockTtl));
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getLockOwner()).isNotBlank();
        assertThat(staleView.getLockUntil()).isEqualTo(FIXED_NOW.plus(configuredLockTtl));
        assertThat(staleView.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(staleView.getLockOwner()).isEqualTo(saved.getLockOwner());
    }

    @Test
    void releaseUsesInjectedClockForUpdatedAt() {
        CheckSchedule staleView = schedule(2L);
        staleView.setLockOwner("worker-1");

        CheckSchedule current = schedule(2L);
        current.setLockOwner("worker-1");
        current.setLockUntil(FIXED_NOW.plusMinutes(2));

        when(checkScheduleRepository.findById(2L)).thenReturn(Optional.of(current));
        when(checkScheduleRepository.save(org.mockito.ArgumentMatchers.any(CheckSchedule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleClaimService service = new ScheduleClaimService(checkScheduleRepository, FIXED_CLOCK);

        service.release(staleView);

        ArgumentCaptor<CheckSchedule> captor = ArgumentCaptor.forClass(CheckSchedule.class);
        verify(checkScheduleRepository).save(captor.capture());
        CheckSchedule saved = captor.getValue();
        assertThat(saved.getLockUntil()).isNull();
        assertThat(saved.getLockOwner()).isNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(staleView.getLockUntil()).isNull();
        assertThat(staleView.getLockOwner()).isNull();
        assertThat(staleView.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void releaseDoesNothingWhenCurrentLockOwnerDiffers() {
        CheckSchedule staleView = schedule(3L);
        staleView.setLockOwner("worker-1");
        staleView.setLockUntil(FIXED_NOW.plusMinutes(2));
        staleView.setUpdatedAt(FIXED_NOW.minusMinutes(1));

        CheckSchedule current = schedule(3L);
        current.setLockOwner("worker-2");
        current.setLockUntil(FIXED_NOW.plusMinutes(3));
        current.setUpdatedAt(FIXED_NOW);

        when(checkScheduleRepository.findById(3L)).thenReturn(Optional.of(current));

        ScheduleClaimService service = new ScheduleClaimService(checkScheduleRepository, FIXED_CLOCK);

        service.release(staleView);

        verify(checkScheduleRepository, never()).save(org.mockito.ArgumentMatchers.any(CheckSchedule.class));
        assertThat(staleView.getLockOwner()).isEqualTo("worker-1");
        assertThat(staleView.getLockUntil()).isEqualTo(FIXED_NOW.plusMinutes(2));
        assertThat(staleView.getUpdatedAt()).isEqualTo(FIXED_NOW.minusMinutes(1));
    }

    private CheckSchedule schedule(Long id) {
        CheckSchedule schedule = new CheckSchedule();
        schedule.setId(id);
        schedule.setCheckType(CheckType.PZP_CHECK);
        schedule.setEnabled(true);
        schedule.setNextRunAt(FIXED_NOW.minusMinutes(1));
        return schedule;
    }
}
