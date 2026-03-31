package com.carwatch.application.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.carwatch.domain.obligation.ExpiryStatus;
import com.carwatch.domain.obligation.ObligationState;
import com.carwatch.domain.obligation.ObligationStateRepository;
import com.carwatch.domain.obligation.ObligationType;
import com.carwatch.domain.schedule.CheckOutcome;
import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckType;
import com.carwatch.domain.schedule.RunStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ObligationUpdateServiceTest {

    @Mock
    private ObligationStateRepository obligationStateRepository;

    @Test
    void keepsPreviousExpiryWhenOutcomeHasError() {
        ObligationState existing = new ObligationState();
        existing.setCarId(1L);
        existing.setObligationType(ObligationType.STK);
        existing.setExpiryDate(LocalDate.of(2026, 2, 10));
        existing.setStatus(ExpiryStatus.VALID);

        when(obligationStateRepository.findByCarIdAndObligationType(1L, ObligationType.STK))
                .thenReturn(Optional.of(existing));
        when(obligationStateRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        CheckSchedule schedule = new CheckSchedule();
        schedule.setCarId(1L);
        schedule.setCheckType(CheckType.STK_CHECK);
        schedule.setWarningDaysBefore(20);
        CheckOutcome outcome = new CheckOutcome(RunStatus.ERROR, "fail", null, "{}");

        ObligationUpdateService service = new ObligationUpdateService(obligationStateRepository);
        service.updateFromOutcome(schedule, outcome);

        ArgumentCaptor<ObligationState> captor = ArgumentCaptor.forClass(ObligationState.class);
        org.mockito.Mockito.verify(obligationStateRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiryDate()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(captor.getValue().getStatus()).isEqualTo(ExpiryStatus.ERROR);
    }
}
