package com.carwatch.web.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.carwatch.application.car.CarManagementService;
import com.carwatch.application.car.CreateCarCommand;
import com.carwatch.application.car.UpdateCarCommand;
import com.carwatch.domain.car.Car;
import com.carwatch.domain.vignette.CountryCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class CarControllerWebMvcTest {

    private MockMvc mockMvc;
    private CarManagementService carManagementService;

    @BeforeEach
    void setUp() {
        this.carManagementService = mock(CarManagementService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setUseIsoFormat(true);
        registrar.registerFormatters(conversionService);

        this.mockMvc = MockMvcBuilders.standaloneSetup(new CarController(carManagementService))
                .setValidator(validator)
                .setConversionService(conversionService)
                .build();
    }

    @Test
    void listPageRendersCars() throws Exception {
        Car car = new Car();
        car.setId(11L);
        car.setName("Octavia");
        when(carManagementService.findAll()).thenReturn(List.of(car));

        mockMvc.perform(get("/cars"))
                .andExpect(status().isOk())
                .andExpect(view().name("cars/list"))
                .andExpect(model().attributeExists("cars"))
                .andExpect(model().attribute("cars", hasSize(1)));
    }

    @Test
    void createFormRenders() throws Exception {
        mockMvc.perform(get("/cars/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("cars/form"))
                .andExpect(model().attributeExists("carForm"))
                .andExpect(model().attribute("formMode", "add"));
    }

    @Test
    void createPostRedirectsOnSuccess() throws Exception {
        mockMvc.perform(post("/cars")
                        .param("name", "Superb")
                        .param("licensePlate", "BA123AA")
                        .param("registrationDate", "2024-01-10")
                        .param("vin", "VIN123456789")
                        .param("vignetteSk", "true")
                        .param("vignetteAt", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cars"));

        ArgumentCaptor<CreateCarCommand> commandCaptor = ArgumentCaptor.forClass(CreateCarCommand.class);
        verify(carManagementService).createCar(commandCaptor.capture());
        CreateCarCommand command = commandCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.name()).isEqualTo("Superb");
        org.assertj.core.api.Assertions.assertThat(command.licensePlate()).isEqualTo("BA123AA");
        org.assertj.core.api.Assertions.assertThat(command.registrationDate()).isEqualTo(LocalDate.parse("2024-01-10"));
        org.assertj.core.api.Assertions.assertThat(command.vignetteCountries())
                .containsExactlyInAnyOrder(CountryCode.SK, CountryCode.AT);
    }

    @Test
    void createPostReturnsFormOnValidationFailure() throws Exception {
        mockMvc.perform(post("/cars")
                        .param("name", "")
                        .param("licensePlate", "A")
                        .param("vin", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("cars/form"))
                .andExpect(model().attribute("formMode", "add"))
                .andExpect(model().attributeHasErrors("carForm"))
                .andExpect(model().attributeHasFieldErrors("carForm", "name", "licensePlate", "registrationDate", "vin"));

        verifyNoInteractions(carManagementService);
    }

    @Test
    void editPageRendersForExistingCar() throws Exception {
        Car car = new Car();
        car.setId(7L);
        car.setName("Fabia");
        car.setLicensePlate("KE456BB");
        car.setRegistrationDate(LocalDate.parse("2020-05-20"));
        car.setVin("VIN777");
        car.setVersion(3);
        when(carManagementService.findById(7L)).thenReturn(Optional.of(car));

        mockMvc.perform(get("/cars/7/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("cars/form"))
                .andExpect(model().attribute("formMode", "edit"))
                .andExpect(model().attributeExists("carForm"));
    }

    @Test
    void editPageReturnsNotFoundForMissingCar() throws Exception {
        when(carManagementService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/cars/999/edit"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateReturnsFormOnOptimisticLockFailure() throws Exception {
        doThrow(new OptimisticLockingFailureException("conflict"))
                .when(carManagementService).updateCar(any(UpdateCarCommand.class));

        mockMvc.perform(post("/cars/5")
                        .param("id", "5")
                        .param("name", "Kamiq")
                        .param("licensePlate", "BA111CC")
                        .param("registrationDate", "2022-08-09")
                        .param("vin", "VIN555")
                        .param("version", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("cars/form"))
                .andExpect(model().attribute("formMode", "edit"))
                .andExpect(model().attributeHasErrors("carForm"));
    }

    @Test
    void deactivateRedirectsOnSuccess() throws Exception {
        when(carManagementService.deactivateCar(3L)).thenReturn(new Car());

        mockMvc.perform(post("/cars/3/deactivate"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cars"));

        verify(carManagementService).deactivateCar(3L);
    }
}
