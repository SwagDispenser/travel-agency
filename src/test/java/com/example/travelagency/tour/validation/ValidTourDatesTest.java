package com.example.travelagency.tour.validation;

import com.example.travelagency.tour.TourRequest;
import com.example.travelagency.web.TourForm;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidTourDatesTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.byDefaultProvider().configure()
                .clockProvider(() -> Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-06-01", "2030-06-05"})
    void validate_endOnOrAfterStart_hasNoViolations(String endDate) {
        TourRequest request = request(LocalDate.of(2030, 6, 1), LocalDate.parse(endDate));

        var violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void validate_endBeforeStart_automaticallyInvokesConstraintWithClearFieldMessage() {
        TourRequest request = request(LocalDate.of(2030, 6, 5), LocalDate.of(2030, 6, 1));

        var violations = validator.validate(request);

        assertEquals(1, violations.size());
        ConstraintViolation<TourRequest> violation = violations.iterator().next();
        assertEquals(ValidTourDates.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
        assertEquals("endDate", violation.getPropertyPath().toString());
        assertEquals("Tour end date must be on or after start date", violation.getMessage());
    }

    @ParameterizedTest
    @CsvSource({",2030-06-05,startDate", "2030-06-01,,endDate"})
    void validate_missingDate_reportsNotNullWithoutAnExtraDateRangeError(
            LocalDate startDate, LocalDate endDate, String field) {
        TourRequest request = request(startDate, endDate);

        var violations = validator.validate(request);

        assertEquals(1, violations.size());
        ConstraintViolation<TourRequest> violation = violations.iterator().next();
        assertEquals(NotNull.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
        assertEquals(field, violation.getPropertyPath().toString());
    }

    @ParameterizedTest
    @CsvSource({"2030-06-01,0", "2030-06-05,0", "2030-05-31,1"})
    void validate_htmlForm_usesTheSameDateRule(LocalDate endDate, int expectedErrors) {
        TourForm form = new TourForm();
        form.setTitle("Rome");
        form.setCountry("Italy");
        form.setCity("Rome");
        form.setStartDate(LocalDate.of(2030, 6, 1));
        form.setEndDate(endDate);
        form.setPrice(new BigDecimal("450.00"));
        form.setAvailableSeats(5);

        var violations = validator.validate(form);

        assertEquals(expectedErrors, violations.size());
        violations.forEach(violation -> {
            assertEquals(ValidTourDates.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
            assertEquals("endDate", violation.getPropertyPath().toString());
            assertEquals("Tour end date must be on or after start date", violation.getMessage());
        });
    }

    private static TourRequest request(LocalDate startDate, LocalDate endDate) {
        return new TourRequest("Rome", "Italy", "Rome", "Guided tour", startDate, endDate,
                new BigDecimal("450.00"), 5, false);
    }
}
