package com.example.travelagency.booking.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TourLookupExceptionHandler {
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    ProblemDetail invalidInput(jakarta.validation.ConstraintViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(TourLookupNotFoundException.class)
    ProblemDetail notFound(TourLookupNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(TourLookupUnavailableException.class)
    ProblemDetail unavailable(TourLookupUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }
}
