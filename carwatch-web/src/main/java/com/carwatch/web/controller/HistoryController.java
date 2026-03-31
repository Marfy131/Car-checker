package com.carwatch.web.controller;

import com.carwatch.domain.schedule.CheckRunLogRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HistoryController {

    private final CheckRunLogRepository checkRunLogRepository;

    public HistoryController(CheckRunLogRepository checkRunLogRepository) {
        this.checkRunLogRepository = checkRunLogRepository;
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("runs", checkRunLogRepository.findRecentRuns(100));
        return "history/list";
    }
}
