package com.carwatch.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.carwatch.domain.schedule.CheckRunLog;
import com.carwatch.domain.schedule.CheckRunLogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoryQueryServiceTest {

    @Test
    void findRecentRunsUsesConfiguredPageSize() {
        CheckRunLogRepository checkRunLogRepository = mock(CheckRunLogRepository.class);
        int configuredPageSize = 42;
        List<CheckRunLog> runs = List.of(mock(CheckRunLog.class));
        when(checkRunLogRepository.findRecentRuns(configuredPageSize)).thenReturn(runs);

        HistoryQueryService historyQueryService = new HistoryQueryService(checkRunLogRepository, configuredPageSize);

        assertThat(historyQueryService.findRecentRuns()).isSameAs(runs);
        verify(checkRunLogRepository).findRecentRuns(configuredPageSize);
        verifyNoMoreInteractions(checkRunLogRepository);
    }
}
