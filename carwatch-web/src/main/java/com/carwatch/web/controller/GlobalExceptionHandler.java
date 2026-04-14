package com.carwatch.web.controller;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice(annotations = Controller.class)
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView handleResponseStatusException(ResponseStatusException ex) {
        if (ex.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
            throw ex;
        }
        return errorView(HttpStatus.NOT_FOUND, "error/404");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ModelAndView handleMissingRoute() {
        return errorView(HttpStatus.NOT_FOUND, "error/404");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ModelAndView handleOptimisticLockingFailure() {
        return errorView(HttpStatus.CONFLICT, "error/concurrent-edit");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception ex) throws Exception {
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            throw ex;
        }
        return errorView(HttpStatus.INTERNAL_SERVER_ERROR, "error/500");
    }

    private ModelAndView errorView(HttpStatus status, String viewName) {
        ModelAndView modelAndView = new ModelAndView(viewName);
        modelAndView.setStatus(status);
        return modelAndView;
    }
}
