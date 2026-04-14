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
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/cars")
public class CarController {

    private static final String ATTR_FORM_MODE = "formMode";
    private static final String ATTR_CAR_FORM = "carForm";
    private static final String VIEW_FORM = "cars/form";
    private static final String REDIRECT_CARS = "redirect:/cars";

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
        model.addAttribute(ATTR_CAR_FORM, new CarForm());
        model.addAttribute(ATTR_FORM_MODE, "add");
        return VIEW_FORM;
    }

    @PostMapping
    public String createCar(
            @Valid @ModelAttribute(ATTR_CAR_FORM) CarForm carForm,
            BindingResult bindingResult,
            Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_FORM_MODE, "add");
            return VIEW_FORM;
        }

        carService.createCar(new CreateCarCommand(
                carForm.getName(),
                carForm.getLicensePlate(),
                carForm.getRegistrationDate(),
                carForm.getVin(),
                toVignetteCountries(carForm)));
        return REDIRECT_CARS;
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        Car car = carService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found"));

        populateEditForm(model, car);
        return VIEW_FORM;
    }

    @PostMapping("/{id}")
    public String updateCar(
            @PathVariable Long id,
            @Valid @ModelAttribute(ATTR_CAR_FORM) CarForm carForm,
            BindingResult bindingResult,
            Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_FORM_MODE, "edit");
            return VIEW_FORM;
        }

        try {
            carService.updateCar(new UpdateCarCommand(
                    id,
                    carForm.getName(),
                    carForm.getLicensePlate(),
                    carForm.getRegistrationDate(),
                    carForm.getVin(),
                    carForm.getVersion()));
            return REDIRECT_CARS;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found", ex);
        } catch (OptimisticLockingFailureException _) {
            bindingResult.reject("validation.version.conflict");
            model.addAttribute(ATTR_FORM_MODE, "edit");
            return VIEW_FORM;
        }
    }

    @PostMapping("/{id}/deactivate")
    public String deactivateCar(@PathVariable Long id, @RequestParam int version, Model model) {
        try {
            carService.deactivateCar(id, version);
            return REDIRECT_CARS;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found", ex);
        } catch (OptimisticLockingFailureException _) {
            Car car = carService.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found"));
            CarForm form = populateEditForm(model, car);
            BindingResult bindingResult = new BeanPropertyBindingResult(form, ATTR_CAR_FORM);
            bindingResult.reject("validation.version.conflict");
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + ATTR_CAR_FORM, bindingResult);
            return VIEW_FORM;
        }
    }

    private CarForm populateEditForm(Model model, Car car) {
        CarForm form = new CarForm();
        form.setId(car.getId());
        form.setName(car.getName());
        form.setLicensePlate(car.getLicensePlate());
        form.setRegistrationDate(car.getRegistrationDate());
        form.setVin(car.getVin());
        form.setVersion(car.getVersion());

        model.addAttribute(ATTR_CAR_FORM, form);
        model.addAttribute(ATTR_FORM_MODE, "edit");
        return form;
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
