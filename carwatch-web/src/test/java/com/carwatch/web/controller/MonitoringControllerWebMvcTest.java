package com.carwatch.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MonitoringControllerWebMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ViewResolver viewResolver = (viewName, locale) -> new StubView();
        this.mockMvc = MockMvcBuilders.standaloneSetup(new MonitoringController())
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void monitoringPageResolves() throws Exception {
        mockMvc.perform(get("/monitoring"))
                .andExpect(status().isOk())
                .andExpect(view().name("monitoring"));
    }

    private static final class StubView implements View {

        @Override
        public String getContentType() {
            return "text/html";
        }

        @Override
        public void render(java.util.Map<String, ?> model, jakarta.servlet.http.HttpServletRequest request,
                jakarta.servlet.http.HttpServletResponse response) {
        }
    }
}
