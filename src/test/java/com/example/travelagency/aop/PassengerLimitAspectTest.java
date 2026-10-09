package com.example.travelagency.aop;

import com.example.travelagency.tour.InvalidPassengerCountException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassengerLimitAspectTest {
    @Mock ProceedingJoinPoint joinPoint;
    private final PassengerLimitAspect aspect = new PassengerLimitAspect();

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10})
    void validate_allowedPassengerCount_callsProceed(int passengers) throws Throwable {
        Object expected = new Object();
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L, passengers});
        when(joinPoint.proceed()).thenReturn(expected);

        Object result = aspect.validate(joinPoint, rule("normal"));

        assertSame(expected, result);
        verify(joinPoint).proceed();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 11})
    void validate_outsideRange_throwsWithoutProceed(int passengers) throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L, passengers});

        InvalidPassengerCountException error = assertThrows(InvalidPassengerCountException.class,
                () -> aspect.validate(joinPoint, rule("normal")));

        assertEquals("Passenger count must be between 1 and 10", error.getMessage());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void validate_wrongArgumentType_reportsMisconfiguredAnnotation() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L, "two"});

        assertThrows(IllegalStateException.class, () -> aspect.validate(joinPoint, rule("normal")));

        verify(joinPoint, never()).proceed();
    }

    @ParameterizedTest
    @ValueSource(strings = {"negativeIndex", "missingIndex", "invalidMin", "invalidMax"})
    void validate_invalidAnnotationSettings_reportsConfigurationError(String method) throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L, 2});

        assertThrows(IllegalStateException.class, () -> aspect.validate(joinPoint, rule(method)));

        verify(joinPoint, never()).proceed();
    }

    @Test
    void validate_targetThrows_propagatesSameException() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L, 2});
        IllegalStateException failure = new IllegalStateException("lookup failed");
        when(joinPoint.proceed()).thenThrow(failure);

        Throwable result = assertThrows(IllegalStateException.class,
                () -> aspect.validate(joinPoint, rule("normal")));

        assertSame(failure, result);
    }

    private static PassengerLimit rule(String method) throws NoSuchMethodException {
        return Rules.class.getDeclaredMethod(method).getAnnotation(PassengerLimit.class);
    }

    private static class Rules {
        @PassengerLimit void normal() { }
        @PassengerLimit(argumentIndex = -1) void negativeIndex() { }
        @PassengerLimit(argumentIndex = 2) void missingIndex() { }
        @PassengerLimit(min = 0) void invalidMin() { }
        @PassengerLimit(min = 5, max = 4) void invalidMax() { }
    }
}
