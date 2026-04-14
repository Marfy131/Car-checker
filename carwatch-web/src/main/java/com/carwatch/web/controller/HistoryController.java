package com.carwatch.web.controller;

import com.carwatch.application.history.HistoryQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HistoryController {

    private final HistoryQueryService historyQueryService;

    public HistoryController(HistoryQueryService historyQueryService) {
        this.historyQueryService = historyQueryService;
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("runs", historyQueryService.findRecentRuns());
        return "history/list";
    }
}
