package com.carwatch.web.controller;

import com.carwatch.application.car.CarManagementService;
import com.carwatch.application.car.CreateCarCommand;
import com.carwatch.application.car.UpdateCarCommand;
import com.carwatch.domain.car.Car;
import com.carwatch.domain.vignette.CountryCode;
import com.carwatch.web.form.CarForm;
import jakarta.validation.Valid;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/cars")
public class CarController {

    private final CarManagementService carService;

    public CarController(CarManagementService carService) {
        this.carService = carService;
    }

    @GetMapping
    public String listCars(Model model) {
        model.addAttribute("cars", carService.findAll());
        return "cars/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("carForm", new CarForm());
        model.addAttribute("formMode", "add");
        return "cars/form";
    }

    @PostMapping
    public String createCar(
            @Valid @ModelAttribute("carForm") CarForm carForm,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("formMode", "add");
            return "cars/form";
        }

        carService.createCar(new CreateCarCommand(
                carForm.getName(),
                carForm.getLicensePlate(),
                carForm.getRegistrationDate(),
                carForm.getVin(),
                toVignetteCountries(carForm)
        ));
        return "redirect:/cars";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        Car car = carService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found"));

        CarForm form = new CarForm();
        form.setId(car.getId());
        form.setName(car.getName());
        form.setLicensePlate(car.getLicensePlate());
        form.setRegistrationDate(car.getRegistrationDate());
        form.setVin(car.getVin());
        form.setVersion(car.getVersion());

        model.addAttribute("carForm", form);
        model.addAttribute("formMode", "edit");
        return "cars/form";
    }

    @PostMapping("/{id}")
    public String updateCar(
            @PathVariable Long id,
            @Valid @ModelAttribute("carForm") CarForm carForm,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("formMode", "edit");
            return "cars/form";
        }

        try {
            carService.updateCar(new UpdateCarCommand(
                    id,
                    carForm.getName(),
                    carForm.getLicensePlate(),
                    carForm.getRegistrationDate(),
                    carForm.getVin(),
                    carForm.getVersion()
            ));
            return "redirect:/cars";
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found", ex);
        } catch (OptimisticLockingFailureException ex) {
            bindingResult.reject("validation.version.conflict");
            model.addAttribute("formMode", "edit");
            return "cars/form";
        }
    }

    @PostMapping("/{id}/deactivate")
    public String deactivateCar(@PathVariable Long id) {
        try {
            carService.deactivateCar(id);
            return "redirect:/cars";
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found", ex);
        }
    }

    private Set<CountryCode> toVignetteCountries(CarForm carForm) {
        Set<CountryCode> countries = EnumSet.noneOf(CountryCode.class);
        if (carForm.isVignetteSk()) {
            countries.add(CountryCode.SK);
        }
        if (carForm.isVignetteCz()) {
            countries.add(CountryCode.CZ);
        }
        if (carForm.isVignetteAt()) {
            countries.add(CountryCode.AT);
        }
        return countries;
    }
}
