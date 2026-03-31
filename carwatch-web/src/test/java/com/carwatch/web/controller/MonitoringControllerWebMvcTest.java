package com.carwatch.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MonitoringController.class)
@Import(MonitoringController.class)
class MonitoringControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void monitoringPageResolves() throws Exception {
        mockMvc.perform(get("/monitoring"))
                .andExpect(status().isOk())
                .andExpect(view().name("monitoring"));
    }
}
