package com.carwatch.web.controller;

import com.carwatch.application.car.CarManagementService;
import com.carwatch.application.obligation.ObligationForm;
import com.carwatch.application.obligation.ObligationManagementService;
import com.carwatch.domain.car.Car;
import com.carwatch.domain.obligation.ObligationType;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ObligationController {

    private final CarManagementService carManagementService;
    private final ObligationManagementService obligationManagementService;

    public ObligationController(CarManagementService carManagementService,
            ObligationManagementService obligationManagementService) {
        this.carManagementService = carManagementService;
        this.obligationManagementService = obligationManagementService;
    }

    @GetMapping("/cars/{carId}/obligations")
    public String showObligations(@PathVariable Long carId, Model model) {
        Car car = carManagementService.findById(carId)
                .orElseThrow(() -> new IllegalArgumentException("Car not found: " + carId));
        Set<ObligationType> activeTypes = obligationManagementService.findActiveTypes(carId);
        ObligationForm form = obligationManagementService.buildForm(carId);
        model.addAttribute("car", car);
        model.addAttribute("activeTypes", activeTypes);
        model.addAttribute("obligationForm", form);
        return "cars/obligations";
    }

    @PostMapping("/cars/{carId}/obligations")
    public String saveObligations(@PathVariable Long carId,
            @ModelAttribute("obligationForm") ObligationForm form,
            RedirectAttributes redirectAttributes) {
        obligationManagementService.saveObligations(carId, form);
        redirectAttributes.addFlashAttribute("savedOk", true);
        return "redirect:/cars/" + carId + "/obligations";
    }
}
