package com.carwatch.web.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.carwatch.application.history.HistoryQueryService;
import com.carwatch.domain.schedule.CheckRunLog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HistoryController.class)
@Import(HistoryController.class)
class HistoryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HistoryQueryService historyQueryService;

    @Test
    void getHistoryRendersRecentRunsFromService() throws Exception {
        CheckRunLog run = org.mockito.Mockito.mock(CheckRunLog.class);
        List<CheckRunLog> runs = List.of(run);
        when(historyQueryService.findRecentRuns()).thenReturn(runs);

        mockMvc.perform(get("/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("history/list"))
                .andExpect(model().attributeExists("runs"))
                .andExpect(model().attribute("runs", runs));

        verify(historyQueryService).findRecentRuns();
    }
}
