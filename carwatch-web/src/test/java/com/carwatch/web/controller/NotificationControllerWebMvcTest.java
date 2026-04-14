package com.carwatch.web.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.carwatch.application.notification.NotificationQueryService;
import com.carwatch.domain.notification.NotificationChannel;
import com.carwatch.domain.notification.NotificationLog;
import com.carwatch.domain.notification.NotificationStatus;
import com.carwatch.domain.notification.NotificationType;
import com.carwatch.domain.obligation.ObligationType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = NotificationController.class)
@Import(NotificationController.class)
class NotificationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationQueryService notificationQueryService;

    @Test
    void notificationsPageRendersRecentNotificationsFromService() throws Exception {
        NotificationLog notification = new NotificationLog();
        notification.setId(17L);
        notification.setCarId(4L);
        notification.setObligationType(ObligationType.STK);
        notification.setNotificationType(NotificationType.EXPIRY_WARNING);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setRecipient("driver@example.com");
        notification.setSubject("STK warning");
        notification.setSentAt(LocalDateTime.of(2026, 4, 13, 8, 30));
        notification.setStatus(NotificationStatus.SENT);
        when(notificationQueryService.findRecentNotifications()).thenReturn(List.of(notification));

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(view().name("notifications/list"))
                .andExpect(model().attributeExists("notifications"))
                .andExpect(model().attribute("notifications", hasSize(1)))
                .andExpect(model().attribute("notifications", contains(allOf(
                        hasProperty("id", is(17L)),
                        hasProperty("carId", is(4L)),
                        hasProperty("obligationType", is(ObligationType.STK)),
                        hasProperty("notificationType", is(NotificationType.EXPIRY_WARNING)),
                        hasProperty("channel", is(NotificationChannel.EMAIL)),
                        hasProperty("recipient", is("driver@example.com")),
                        hasProperty("subject", is("STK warning")),
                        hasProperty("status", is(NotificationStatus.SENT))
                ))));

        verify(notificationQueryService).findRecentNotifications();
    }
}
