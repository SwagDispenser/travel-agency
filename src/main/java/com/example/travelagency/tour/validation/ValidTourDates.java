package com.example.travelagency.tour.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target(TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = TourDatesValidator.class)
public @interface ValidTourDates {
    String message() default "Tour end date must be on or after start date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
