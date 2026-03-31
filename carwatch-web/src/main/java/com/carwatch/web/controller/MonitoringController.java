package com.carwatch.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MonitoringController {

    @GetMapping("/monitoring")
    public String monitoring() {
        return "monitoring";
    }
}
