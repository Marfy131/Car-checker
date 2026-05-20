package com.carwatch.web.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = {
        GlobalExceptionHandlerWebMvcTest.NotFoundController.class,
        GlobalExceptionHandlerWebMvcTest.ConcurrentEditController.class,
        GlobalExceptionHandlerWebMvcTest.GenericErrorController.class,
        GlobalExceptionHandlerWebMvcTest.BadRequestController.class
})
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerWebMvcTest {

    // @WebMvcTest with inner-class controllers does not find them via component scan
    // (test-classpath classes are not in the scanned package path), so we register
    // them explicitly via @TestConfiguration.
    @TestConfiguration
    static class StubControllerConfig {
        @Bean NotFoundController notFoundController() { return new NotFoundController(); }
        @Bean ConcurrentEditController concurrentEditController() { return new ConcurrentEditController(); }
        @Bean GenericErrorController genericErrorController() { return new GenericErrorController(); }
        @Bean BadRequestController badRequestController() { return new BadRequestController(); }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void notFoundResponseStatusExceptionRenders404Page() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void missingRouteReturns404Status() throws Exception {
        // NoResourceFoundException is handled by DefaultHandlerExceptionResolver (higher priority
        // than @ControllerAdvice), so we can only assert status here — the error/404 template is
        // served by Spring Boot's BasicErrorController which is not loaded in @WebMvcTest.
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void optimisticLockingFailureRendersConcurrentEditPage() throws Exception {
        mockMvc.perform(get("/test/concurrent-edit"))
                .andExpect(status().isConflict())
                .andExpect(view().name("error/concurrent-edit"));
    }

    @Test
    void genericExceptionRenders500Page() throws Exception {
        mockMvc.perform(get("/test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"));
    }

    @Test
    void missingRequestParameterKeepsBadRequestStatus() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertNotEquals(
                        "error/500",
                        result.getModelAndView() == null ? null : result.getModelAndView().getViewName()
                ));
    }

    @Controller
    static class NotFoundController {

        @GetMapping("/test/not-found")
        String fail() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Missing");
        }
    }

    @Controller
    static class ConcurrentEditController {

        @GetMapping("/test/concurrent-edit")
        String fail() {
            throw new OptimisticLockingFailureException("Conflict");
        }
    }

    @Controller
    static class GenericErrorController {

        @GetMapping("/test/error")
        String fail() {
            throw new IllegalStateException("Boom");
        }
    }

    @Controller
    static class BadRequestController {

        @GetMapping("/test/bad-request")
        String fail(@RequestParam String required) {
            return "ignored";
        }
    }
}
