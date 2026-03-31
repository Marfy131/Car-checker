package com.carwatch.web.controller;

import com.carwatch.domain.notification.NotificationLogRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NotificationController {

    private final NotificationLogRepository notificationLogRepository;

    public NotificationController(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @GetMapping("/notifications")
    public String notifications(Model model) {
        model.addAttribute("notifications", notificationLogRepository.findRecent(100));
        return "notifications/list";
    }
}
