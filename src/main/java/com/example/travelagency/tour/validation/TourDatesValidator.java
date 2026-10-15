package com.example.travelagency.tour.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TourDatesValidator implements ConstraintValidator<ValidTourDates, TourDateRange> {
    @Override
    public boolean isValid(TourDateRange tour, ConstraintValidatorContext context) {

        if (tour == null || tour.startDate() == null || tour.endDate() == null) {
            return true;
        }
        if (!tour.endDate().isBefore(tour.startDate())) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("endDate")
                .addConstraintViolation();
        return false;
    }
}
