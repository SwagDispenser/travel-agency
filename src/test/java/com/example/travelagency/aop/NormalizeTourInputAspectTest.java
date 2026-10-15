package com.example.travelagency.aop;

import com.example.travelagency.tour.TourRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NormalizeTourInputAspectTest {
    @Mock ProceedingJoinPoint joinPoint;
    private final NormalizeTourInputAspect aspect = new NormalizeTourInputAspect();

    @Test
    void normalize_requestWithSpaces_passesCleanCopyAndPreservesOtherArguments() throws Throwable {
        TourRequest request = request("  Rome  ", "  City tour  ");
        Object[] original = {7L, request};
        Object expected = new Object();
        when(joinPoint.getArgs()).thenReturn(original);
        when(joinPoint.proceed(any(Object[].class))).thenReturn(expected);

        Object result = aspect.normalize(joinPoint);

        assertSame(expected, result);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(joinPoint).proceed(arguments.capture());
        assertEquals(7L, arguments.getValue()[0]);
        TourRequest normalized = (TourRequest) arguments.getValue()[1];
        assertEquals(new TourRequest("Rome", "Italy", "Rome", "City tour",
                request.startDate(), request.endDate(), request.price(), 5, true), normalized);
        assertSame(request, original[1]);
        assertEquals("  Rome  ", request.title());
    }

    @Test
    void normalize_nullDescription_preservesNull() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{request("Rome", null)});

        aspect.normalize(joinPoint);

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(joinPoint).proceed(arguments.capture());
        assertNull(((TourRequest) arguments.getValue()[0]).description());
    }

    @Test
    void normalize_noTourRequest_passesArgumentsThrough() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L});

        aspect.normalize(joinPoint);

        verify(joinPoint).proceed(new Object[]{7L});
    }

    @Test
    void normalize_targetThrows_propagatesSameException() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{request("Rome", null)});
        IllegalStateException failure = new IllegalStateException("save failed");
        when(joinPoint.proceed(any(Object[].class))).thenThrow(failure);

        Throwable result = assertThrows(IllegalStateException.class, () -> aspect.normalize(joinPoint));

        assertSame(failure, result);
    }

    private static TourRequest request(String title, String description) {
        return new TourRequest(title, " Italy ", " Rome ", description,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 5), new BigDecimal("220.00"), 5, true);
    }
}
