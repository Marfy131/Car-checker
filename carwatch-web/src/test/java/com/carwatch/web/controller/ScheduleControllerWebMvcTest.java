package com.carwatch.web.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.carwatch.application.schedule.ScheduleManagementService;
import com.carwatch.domain.schedule.CheckSchedule;
import com.carwatch.domain.schedule.CheckType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ScheduleController.class)
@Import(ScheduleController.class)
class ScheduleControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleManagementService scheduleManagementService;

    @Test
    void listPageRendersSchedulesFromService() throws Exception {
        CheckSchedule schedule = new CheckSchedule();
        schedule.setId(11L);
        schedule.setCarId(7L);
        schedule.setCheckType(CheckType.STK_CHECK);
        schedule.setCronExpression("0 0 6 * * *");
        schedule.setWarningDaysBefore(30);
        schedule.setEnabled(true);
        when(scheduleManagementService.findAll()).thenReturn(List.of(schedule));

        mockMvc.perform(get("/schedules"))
                .andExpect(status().isOk())
                .andExpect(view().name("schedules/list"))
                .andExpect(model().attributeExists("schedules"))
                .andExpect(model().attribute("schedules", hasSize(1)));

        verify(scheduleManagementService).findAll();
    }

    @Test
    void editPageProjectsScheduleIntoForm() throws Exception {
        CheckSchedule schedule = new CheckSchedule();
        schedule.setId(5L);
        schedule.setCronExpression("0 15 7 * * *");
        schedule.setWarningDaysBefore(21);
        schedule.setEnabled(false);
        when(scheduleManagementService.findById(5L)).thenReturn(schedule);

        mockMvc.perform(get("/schedules/5/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("schedules/form"))
                .andExpect(model().attributeExists("scheduleForm"))
                .andExpect(model().attribute("scheduleForm", allOf(
                        hasProperty("id", is(5L)),
                        hasProperty("cronExpression", is("0 15 7 * * *")),
                        hasProperty("warningDaysBefore", is(21)),
                        hasProperty("enabled", is(false))
                )));

        verify(scheduleManagementService).findById(5L);
    }

    @Test
    void validUpdateDelegatesToServiceAndRedirects() throws Exception {
        mockMvc.perform(post("/schedules/8")
                        .param("id", "8")
                        .param("cronExpression", "0 30 8 * * *")
                        .param("warningDaysBefore", "14")
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/schedules"));

        verify(scheduleManagementService).updateSchedule(8L, "0 30 8 * * *", 14, true);
    }

    @Test
    void invalidUpdateReturnsFormAndDoesNotDelegate() throws Exception {
        mockMvc.perform(post("/schedules/8")
                        .param("id", "8")
                        .param("cronExpression", "")
                        .param("warningDaysBefore", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("schedules/form"))
                .andExpect(model().attributeHasErrors("scheduleForm"))
                .andExpect(model().attributeHasFieldErrors("scheduleForm", "cronExpression", "warningDaysBefore"));

        verifyNoInteractions(scheduleManagementService);
    }

    @Test
    void runNowDelegatesToServiceAndRedirects() throws Exception {
        mockMvc.perform(post("/schedules/13/run-now"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/schedules"));

        verify(scheduleManagementService).runNow(13L);
    }
}
